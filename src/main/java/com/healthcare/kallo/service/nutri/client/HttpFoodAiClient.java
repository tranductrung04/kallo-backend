package com.healthcare.kallo.service.nutri.client;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.config.NutriProperties;
import java.net.SocketTimeoutException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** HTTP client invoking Modal food scanner predict endpoint with multipart/form-data. */
@Component
@ConditionalOnProperty(
    name = "app.nutri.ai.mock-enabled",
    havingValue = "false",
    matchIfMissing = true)
public class HttpFoodAiClient implements FoodAiClient {

  private static final Logger log = LoggerFactory.getLogger(HttpFoodAiClient.class);
  private static final String DEFAULT_BASE_URL = "http://localhost:8000";

  private final RestClient restClient;
  private final String apiKey;

  @Autowired
  public HttpFoodAiClient(
      NutriProperties nutriProperties,
      @Value("${MODAL_AI_PREDICT_URL:}") String envBaseUrl,
      @Value("${AI_SERVICE_API_KEY:}") String envApiKey) {
    this(
        buildRestClient(resolveBaseUrl(nutriProperties, envBaseUrl), nutriProperties),
        resolveApiKey(nutriProperties, envApiKey));
  }

  HttpFoodAiClient(RestClient restClient, String apiKey) {
    this.restClient = restClient;
    this.apiKey = apiKey != null ? apiKey : "";
  }

  @Override
  public FoodAiDetectResponse detect(byte[] imageBytes, String filename, String contentType) {
    long startTime = System.currentTimeMillis();
    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    String resolvedFilename = sanitizeFilename(filename);
    ByteArrayResource resource =
        new ByteArrayResource(imageBytes) {
          @Override
          public String getFilename() {
            return resolvedFilename;
          }

          @Override
          public long contentLength() {
            return imageBytes.length;
          }
        };
    // Modal FastAPI predict endpoint expects multipart part name "file"
    body.add("file", resource);

    try {
      FoodAiDetectResponse response =
          restClient
              .post()
              .uri("")
              .contentType(MediaType.MULTIPART_FORM_DATA)
              .header("X-Request-Id", UUID.randomUUID().toString())
              .headers(
                  headers -> {
                    if (!apiKey.isBlank()) {
                      headers.set("X-Internal-Api-Key", apiKey);
                    }
                  })
              .body(body)
              .retrieve()
              .body(FoodAiDetectResponse.class);
      long elapsed = System.currentTimeMillis() - startTime;
      log.info(
          "Modal AI inference completed in {} ms (detected={}, label={})",
          elapsed,
          response != null && response.detected(),
          response != null ? response.label() : null);
      return response;
    } catch (ResourceAccessException e) {
      long elapsed = System.currentTimeMillis() - startTime;
      if (isTimeout(e)) {
        log.warn("Modal AI request timed out after {} ms", elapsed, e);
        throw new BusinessException(ErrorCode.AI_SERVICE_TIMEOUT);
      }
      log.warn("Modal AI endpoint unreachable after {} ms", elapsed, e);
      throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
    } catch (RestClientResponseException e) {
      log.warn("Modal AI service returned HTTP {}", e.getStatusCode().value());
      if (e.getStatusCode().is4xxClientError()) {
        throw new BusinessException(ErrorCode.INVALID_FILE);
      }
      throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
    } catch (RestClientException e) {
      log.warn("Modal AI service failed unexpectedly", e);
      throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
    }
  }

  private static String sanitizeFilename(String originalFilename) {
    if (originalFilename == null || originalFilename.isBlank()) {
      return "food_capture.jpg";
    }
    return originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
  }

  private static boolean isTimeout(Throwable ex) {
    Throwable cause = ex;
    while (cause != null) {
      if (cause instanceof SocketTimeoutException) {
        return true;
      }
      cause = cause.getCause();
    }
    return false;
  }

  private static RestClient buildRestClient(String baseUrl, NutriProperties nutriProperties) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout((int) nutriProperties.getAi().getConnectTimeout().toMillis());
    requestFactory.setReadTimeout((int) nutriProperties.getAi().getReadTimeout().toMillis());
    return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
  }

  private static String resolveBaseUrl(NutriProperties nutriProperties, String envBaseUrl) {
    String configured = nutriProperties.getAi().getBaseUrl();
    if (DEFAULT_BASE_URL.equals(configured) && envBaseUrl != null && !envBaseUrl.isBlank()) {
      return envBaseUrl;
    }
    return configured;
  }

  private static String resolveApiKey(NutriProperties nutriProperties, String envApiKey) {
    String configured = nutriProperties.getAi().getApiKey();
    if ((configured == null || configured.isBlank()) && envApiKey != null && !envApiKey.isBlank()) {
      return envApiKey;
    }
    return configured != null ? configured : "";
  }
}
