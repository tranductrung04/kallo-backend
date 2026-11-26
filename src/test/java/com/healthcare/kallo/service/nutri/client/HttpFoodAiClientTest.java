package com.healthcare.kallo.service.nutri.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class HttpFoodAiClientTest {

  private static final String MODAL_URL = "https://mock-modal.example.com/predict";

  private MockRestServiceServer server;
  private HttpFoodAiClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl(MODAL_URL);
    server = MockRestServiceServer.bindTo(builder).build();
    client = new HttpFoodAiClient(builder.build(), "internal-secret");
  }

  @Test
  void postsMultipartFileFieldAndDeserializesModalResponse() {
    String json =
        """
        {
          "detected": true,
          "label": "Banh mi",
          "confidence": 0.954,
          "all_items": [
            {
              "label": "Banh mi",
              "confidence": 0.954
            }
          ]
        }
        """;

    server
        .expect(requestTo(MODAL_URL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("X-Internal-Api-Key", "internal-secret"))
        .andExpect(content().string(containsString("name=\"file\"")))
        .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

    FoodAiClient.FoodAiDetectResponse response =
        client.detect(new byte[] {(byte) 0xFF, (byte) 0xD8}, "meal.jpg", "image/jpeg");

    assertThat(response.detected()).isTrue();
    assertThat(response.label()).isEqualTo("Banh mi");
    assertThat(response.confidence()).isEqualTo(0.954);
    assertThat(response.allItems()).hasSize(1);
    assertThat(response.allItems().get(0).label()).isEqualTo("Banh mi");
    server.verify();
  }

  @Test
  void mapsSocketTimeoutToAiServiceTimeout() {
    server
        .expect(requestTo(MODAL_URL))
        .andRespond(withException(new SocketTimeoutException("Read timed out")));

    assertThatThrownBy(
            () -> client.detect(new byte[] {(byte) 0xFF, (byte) 0xD8}, "meal.jpg", "image/jpeg"))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.AI_SERVICE_TIMEOUT);
    server.verify();
  }

  @Test
  void maps5xxToAiServiceUnavailable() {
    server.expect(requestTo(MODAL_URL)).andRespond(withServerError());

    assertThatThrownBy(
            () -> client.detect(new byte[] {(byte) 0xFF, (byte) 0xD8}, "meal.jpg", "image/jpeg"))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.AI_SERVICE_UNAVAILABLE);
    server.verify();
  }
}
