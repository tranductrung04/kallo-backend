package com.healthcare.kallo.service.nutri.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.config.AiProperties;
import com.healthcare.kallo.dto.response.nutri.RecognizeFoodItemResponse;
import com.healthcare.kallo.dto.response.nutri.RecognizeFoodResponse;
import com.healthcare.kallo.model.food.Food;
import com.healthcare.kallo.model.profile.UserHealthProfile;
import com.healthcare.kallo.repository.food.FoodRepository;
import com.healthcare.kallo.repository.profile.UserHealthProfileRepository;
import com.healthcare.kallo.service.auth.StorageService;
import com.healthcare.kallo.service.nutri.FoodRecognitionService;
import java.io.InputStream;
import java.time.Duration;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FoodRecognitionServiceImpl implements FoodRecognitionService {

  private static final Logger log = LoggerFactory.getLogger(FoodRecognitionServiceImpl.class);
  private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 MB
  private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
      "image/jpeg", "image/png", "image/webp", "image/jpg"
  );

  private final StorageService storageService;
  private final FoodRepository foodRepository;
  private final UserHealthProfileRepository userHealthProfileRepository;
  private final RestClient aiRestClient;

  public FoodRecognitionServiceImpl(
      StorageService storageService,
      FoodRepository foodRepository,
      UserHealthProfileRepository userHealthProfileRepository,
      AiProperties aiProperties) {
    this.storageService = storageService;
    this.foodRepository = foodRepository;
    this.userHealthProfileRepository = userHealthProfileRepository;

    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(2000);
    factory.setReadTimeout(aiProperties.timeoutMs());

    this.aiRestClient = RestClient.builder()
        .baseUrl(aiProperties.serviceUrl())
        .requestFactory(factory)
        .build();
  }

  @Override
  public RecognizeFoodResponse recognizeFood(UUID userId, MultipartFile file) {
    // 1. Validate file
    if (file == null || file.isEmpty()) {
      throw new BusinessException(ErrorCode.INVALID_FILE);
    }
    if (file.getSize() > MAX_FILE_SIZE) {
      throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE);
    }
    String contentType = file.getContentType();
    if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
      throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    // 2. Upload to MinIO
    String ext = getExtension(file.getOriginalFilename(), contentType);
    String objectKey = "meals/" + userId + "/" + UUID.randomUUID() + ext;
    String imageUrl;
    try (InputStream is = file.getInputStream()) {
      storageService.upload(objectKey, is, file.getSize(), contentType);
      imageUrl = storageService.presignedUrl(objectKey, Duration.ofDays(7));
    } catch (Exception ex) {
      log.error("Failed to upload meal image to MinIO: {}", ex.getMessage());
      throw new BusinessException(ErrorCode.INTERNAL_ERROR);
    }

    // 3. Call AI Service
    List<AiPrediction> aiPredictions = callAiService(file);

    // 4. Map predictions to food entities & check allergies
    List<String> userAllergies = userHealthProfileRepository.findByUserId(userId)
        .map(UserHealthProfile::getAllergies)
        .orElse(Collections.emptyList());

    List<RecognizeFoodItemResponse> items = new ArrayList<>();
    for (AiPrediction pred : aiPredictions) {
      if (pred.classId() == 27) { // Class 27 is 'Con nguoi' (NON_FOOD)
        continue;
      }
      Optional<Food> foodOpt = foodRepository.findByVietfoodClassId(pred.classId());
      if (foodOpt.isPresent()) {
        Food food = foodOpt.get();
        List<String> foodAllergies = food.getAllergens();
        List<String> warnings = new ArrayList<>();
        if (foodAllergies != null) {
          for (String fa : foodAllergies) {
            for (String ua : userAllergies) {
              if (fa.equalsIgnoreCase(ua)) {
                warnings.add(fa);
              }
            }
          }
        }

        items.add(new RecognizeFoodItemResponse(
            food.getId(),
            food.getName(),
            pred.classId(),
            pred.confidence(),
            food.getDefaultServingName(),
            food.getDefaultServingGrams(),
            food.getCaloriesPer100g(),
            food.getProteinPer100g(),
            food.getCarbsPer100g(),
            food.getFatPer100g(),
            !warnings.isEmpty(),
            warnings,
            pred.box() != null ? pred.box() : Collections.emptyList()
        ));
      }
    }

    return new RecognizeFoodResponse(imageUrl, items);
  }

  private List<AiPrediction> callAiService(MultipartFile file) {
    try {
      ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
        @Override
        public String getFilename() {
          return file.getOriginalFilename() != null ? file.getOriginalFilename() : "image.jpg";
        }
      };

      MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
      body.add("file", resource);

      AiDetectionResponse response = aiRestClient.post()
          .uri("/ai/v1/detect")
          .contentType(MediaType.MULTIPART_FORM_DATA)
          .body(body)
          .retrieve()
          .body(AiDetectionResponse.class);

      if (response != null && response.predictions() != null) {
        return response.predictions();
      }
    } catch (Exception ex) {
      log.warn("AI Service call failed or service unreachable: {}. Returning empty predictions.", ex.getMessage());
    }
    return Collections.emptyList();
  }

  private String getExtension(String filename, String contentType) {
    if (filename != null && filename.contains(".")) {
      return filename.substring(filename.lastIndexOf("."));
    }
    if ("image/png".equalsIgnoreCase(contentType)) return ".png";
    if ("image/webp".equalsIgnoreCase(contentType)) return ".webp";
    return ".jpg";
  }

  public record AiDetectionResponse(List<AiPrediction> predictions) {}

  public record AiPrediction(
      @JsonProperty("class_id") int classId,
      @JsonProperty("class_name") String className,
      double confidence,
      List<Double> box
  ) {}
}
