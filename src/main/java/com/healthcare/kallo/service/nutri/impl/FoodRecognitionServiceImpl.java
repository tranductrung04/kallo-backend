package com.healthcare.kallo.service.nutri.impl;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.config.NutriProperties;
import com.healthcare.kallo.dto.response.nutri.AllergicIngredientWarning;
import com.healthcare.kallo.dto.response.nutri.FoodRecognitionResponse;
import com.healthcare.kallo.model.food.Food;
import com.healthcare.kallo.repository.food.FoodRepository;
import com.healthcare.kallo.service.nutri.FoodRecognitionService;
import com.healthcare.kallo.service.nutri.NutriAllergyChecker;
import com.healthcare.kallo.service.nutri.NutriImageStorageService;
import com.healthcare.kallo.service.nutri.client.FoodAiClient;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FoodRecognitionServiceImpl implements FoodRecognitionService {

  private static final Logger log = LoggerFactory.getLogger(FoodRecognitionServiceImpl.class);
  private static final String MODEL_VERSION = "modal-food-scanner";
  private static final String EXCLUDED_HUMAN_LABEL = "con nguoi";
  private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
  private static final Pattern MULTI_SPACE = Pattern.compile("\\s+");

  private final FoodAiClient foodAiClient;
  private final NutriImageStorageService imageStorageService;
  private final FoodRepository foodRepository;
  private final NutriAllergyChecker allergyChecker;
  private final NutriProperties nutriProperties;
  private final TransactionTemplate readOnlyTx;

  public FoodRecognitionServiceImpl(
      FoodAiClient foodAiClient,
      NutriImageStorageService imageStorageService,
      FoodRepository foodRepository,
      NutriAllergyChecker allergyChecker,
      NutriProperties nutriProperties) {
    this(
        foodAiClient,
        imageStorageService,
        foodRepository,
        allergyChecker,
        nutriProperties,
        null);
  }

  @Autowired
  public FoodRecognitionServiceImpl(
      FoodAiClient foodAiClient,
      NutriImageStorageService imageStorageService,
      FoodRepository foodRepository,
      NutriAllergyChecker allergyChecker,
      NutriProperties nutriProperties,
      PlatformTransactionManager transactionManager) {
    this.foodAiClient = foodAiClient;
    this.imageStorageService = imageStorageService;
    this.foodRepository = foodRepository;
    this.allergyChecker = allergyChecker;
    this.nutriProperties = nutriProperties;
    if (transactionManager != null) {
      TransactionTemplate template = new TransactionTemplate(transactionManager);
      template.setReadOnly(true);
      this.readOnlyTx = template;
    } else {
      this.readOnlyTx = null;
    }
  }

  @Override
  public FoodRecognitionResponse recognize(UUID userId, MultipartFile image) {
    byte[] bytes = validateAndReadBytes(image);
    String detectedContentType = detectImageMimeType(bytes);
    if (detectedContentType == null) {
      throw new BusinessException(ErrorCode.UNSUPPORTED_IMAGE_TYPE);
    }

    double threshold = nutriProperties.getAi().getConfidenceThreshold().doubleValue();
    String ext = "image/png".equals(detectedContentType) ? "png" : "jpg";
    String tempImageKey = imageStorageService.uploadTempImage(userId, bytes, detectedContentType);

    FoodAiClient.FoodAiDetectResponse aiResponse;
    try {
      aiResponse = foodAiClient.detect(bytes, "meal." + ext, detectedContentType);
    } catch (RuntimeException e) {
      imageStorageService.deleteIfPresent(tempImageKey);
      throw e;
    }

    List<FoodAiClient.DetectedItem> acceptedItems =
        extractValidDistinctItems(aiResponse, threshold);
    if (acceptedItems.isEmpty()) {
      imageStorageService.deleteIfPresent(tempImageKey);
      throw new BusinessException(ErrorCode.NON_FOOD_DETECTED);
    }

    List<FoodRecognitionResponse.RecognizedFoodItem> mappedItems =
        readOnlyTx != null
            ? readOnlyTx.execute(status -> mapRecognizedItems(userId, acceptedItems))
            : mapRecognizedItems(userId, acceptedItems);
    String tempImageUrl = imageStorageService.presignedUrlOrNull(tempImageKey);
    return new FoodRecognitionResponse(tempImageKey, tempImageUrl, MODEL_VERSION, mappedItems);
  }

  private List<FoodRecognitionResponse.RecognizedFoodItem> mapRecognizedItems(
      UUID userId, List<FoodAiClient.DetectedItem> acceptedItems) {
    Set<UUID> allergicIds = allergyChecker.loadUserAllergicIngredientIds(userId);
    List<String> normalizedLabels =
        acceptedItems.stream().map(item -> normalizeLabel(item.label())).distinct().toList();

    // 1 batch SQL lookup against pre-computed lowercase/unaccented name_unaccented column
    Map<String, Food> foodByNormalizedName =
        foodRepository.findActiveSystemFoodsByNameUnaccentedIn(normalizedLabels).stream()
            .filter(food -> food.getDeletedAt() == null)
            .collect(
                Collectors.toMap(
                    food ->
                        normalizeLabel(
                            food.getNameUnaccented() != null
                                ? food.getNameUnaccented()
                                : food.getName()),
                    Function.identity(),
                    (existing, replacement) -> existing,
                    LinkedHashMap::new));

    List<FoodRecognitionResponse.RecognizedFoodItem> mapped =
        new ArrayList<>(acceptedItems.size());
    for (FoodAiClient.DetectedItem item : acceptedItems) {
      String normalized = normalizeLabel(item.label());
      Food food = foodByNormalizedName.get(normalized);
      if (food == null) {
        food =
            foodRepository
                .findBestSystemMatchByUnaccentedLabel(normalized)
                .filter(f -> f.getDeletedAt() == null)
                .orElse(null);
        if (food != null) {
          foodByNormalizedName.put(normalized, food);
        }
      }
      mapped.add(toRecognizedFoodItem(item, food, allergicIds));
    }
    return mapped;
  }

  private FoodRecognitionResponse.RecognizedFoodItem toRecognizedFoodItem(
      FoodAiClient.DetectedItem item, Food food, Set<UUID> allergicIds) {
    BigDecimal defaultServingGrams = food != null ? food.getDefaultServingGrams() : null;
    List<AllergicIngredientWarning> warnings =
        food != null
            ? allergyChecker.checkFoodAllergies(food, allergicIds)
            : Collections.emptyList();

    return new FoodRecognitionResponse.RecognizedFoodItem(
        food != null ? food.getVietfoodClassId() : null,
        item.label(),
        item.confidence(),
        null,
        food != null ? food.getId() : null,
        food != null ? food.getName() : item.label(),
        food != null ? food.getDefaultServingName() : null,
        defaultServingGrams,
        defaultServingGrams,
        1.0,
        food != null ? food.getCaloriesPerServing() : null,
        food != null ? food.getProteinPerServing() : null,
        food != null ? food.getCarbsPerServing() : null,
        food != null ? food.getFatPerServing() : null,
        !warnings.isEmpty(),
        warnings);
  }

  private List<FoodAiClient.DetectedItem> extractValidDistinctItems(
      FoodAiClient.FoodAiDetectResponse response, double threshold) {
    if (response == null || !response.detected()) {
      return Collections.emptyList();
    }

    List<FoodAiClient.DetectedItem> rawItems;
    if (response.allItems() != null && !response.allItems().isEmpty()) {
      rawItems = response.allItems();
    } else if (response.label() != null && response.confidence() != null) {
      rawItems = List.of(new FoodAiClient.DetectedItem(response.label(), response.confidence()));
    } else {
      return Collections.emptyList();
    }

    Map<String, FoodAiClient.DetectedItem> bestByLabel = new LinkedHashMap<>();
    rawItems.stream()
        .filter(item -> item.label() != null && !item.label().isBlank())
        .filter(item -> item.confidence() != null && item.confidence() >= threshold)
        .filter(item -> !EXCLUDED_HUMAN_LABEL.equals(normalizeLabel(item.label())))
        .sorted(Comparator.comparing(FoodAiClient.DetectedItem::confidence).reversed())
        .forEach(item -> bestByLabel.putIfAbsent(normalizeLabel(item.label()), item));

    return List.copyOf(bestByLabel.values());
  }

  static String normalizeLabel(String rawLabel) {
    if (rawLabel == null) {
      return "";
    }
    String replaced = rawLabel.trim().replace('_', ' ').replace('-', ' ');
    String decomposed = Normalizer.normalize(replaced, Normalizer.Form.NFD);
    String unaccented =
        COMBINING_MARKS
            .matcher(decomposed)
            .replaceAll("")
            .replace('đ', 'd')
            .replace('Đ', 'D');
    return MULTI_SPACE.matcher(unaccented.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
  }

  private byte[] validateAndReadBytes(MultipartFile image) {
    if (image == null || image.isEmpty()) {
      log.warn(
          "Rejected food recognition upload: image part is null or empty (name={}, originalFilename={})",
          image != null ? image.getName() : null,
          image != null ? image.getOriginalFilename() : null);
      throw new BusinessException(ErrorCode.INVALID_FILE);
    }
    if (image.getSize() > nutriProperties.getMaxImageBytes()) {
      throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE);
    }
    try {
      byte[] bytes = image.getBytes();
      if (bytes.length == 0) {
        log.warn("Rejected food recognition upload: image bytes length is 0");
        throw new BusinessException(ErrorCode.INVALID_FILE);
      }
      if (bytes.length > nutriProperties.getMaxImageBytes()) {
        throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE);
      }
      return bytes;
    } catch (IOException e) {
      log.warn("Failed to read uploaded food recognition image bytes", e);
      throw new BusinessException(ErrorCode.INVALID_FILE);
    }
  }

  private static String detectImageMimeType(byte[] bytes) {
    if (bytes.length >= 3
        && (bytes[0] & 0xFF) == 0xFF
        && (bytes[1] & 0xFF) == 0xD8
        && (bytes[2] & 0xFF) == 0xFF) {
      return "image/jpeg";
    }
    if (bytes.length >= 8
        && (bytes[0] & 0xFF) == 0x89
        && bytes[1] == 0x50
        && bytes[2] == 0x4E
        && bytes[3] == 0x47
        && bytes[4] == 0x0D
        && bytes[5] == 0x0A
        && bytes[6] == 0x1A
        && bytes[7] == 0x0A) {
      return "image/png";
    }
    return null;
  }
}
