package com.healthcare.kallo.service.food.impl;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.dto.response.food.FoodResponse;
import com.healthcare.kallo.model.food.BarcodeMiss;
import com.healthcare.kallo.model.food.Food;
import com.healthcare.kallo.model.food.enums.FoodCategory;
import com.healthcare.kallo.model.food.enums.FoodSource;
import com.healthcare.kallo.model.food.enums.MealType;
import com.healthcare.kallo.repository.food.BarcodeMissRepository;
import com.healthcare.kallo.repository.food.FoodRepository;
import com.healthcare.kallo.service.food.BarcodeService;
import com.healthcare.kallo.service.food.client.OpenFoodFactsClient;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BarcodeServiceImpl implements BarcodeService {

  private static final Logger log = LoggerFactory.getLogger(BarcodeServiceImpl.class);
  private static final Pattern BARCODE_PATTERN = Pattern.compile("^[0-9]{8,14}$");

  private final FoodRepository foodRepository;
  private final BarcodeMissRepository barcodeMissRepository;
  private final OpenFoodFactsClient openFoodFactsClient;

  public BarcodeServiceImpl(
      FoodRepository foodRepository,
      BarcodeMissRepository barcodeMissRepository,
      OpenFoodFactsClient openFoodFactsClient) {
    this.foodRepository = foodRepository;
    this.barcodeMissRepository = barcodeMissRepository;
    this.openFoodFactsClient = openFoodFactsClient;
  }

  @Override
  @Transactional
  public FoodResponse getFoodByBarcode(String barcode) {
    if (barcode == null || !BARCODE_PATTERN.matcher(barcode.trim()).matches()) {
      throw new BusinessException(ErrorCode.INVALID_BARCODE);
    }
    String cleanBarcode = barcode.trim();

    // 1. Tầng 1: Kiểm tra CSDL nội bộ
    Optional<Food> localFood = foodRepository.findByBarcode(cleanBarcode);
    if (localFood.isPresent()) {
      return FoodResponse.fromEntity(localFood.get());
    }

    // 2. Tầng 2: Kiểm tra bộ đệm BarcodeMisses
    Instant now = Instant.now();
    if (barcodeMissRepository.findByBarcodeAndExpiresAtAfter(cleanBarcode, now).isPresent()) {
      throw new BusinessException(ErrorCode.BARCODE_NOT_FOUND);
    }

    // 3. Tầng 3: Gọi Open Food Facts API v2
    Optional<OpenFoodFactsClient.OffProduct> offOpt =
        openFoodFactsClient.fetchByBarcode(cleanBarcode);

    if (offOpt.isEmpty()) {
      recordMiss(cleanBarcode, now);
      throw new BusinessException(ErrorCode.BARCODE_NOT_FOUND);
    }

    OpenFoodFactsClient.OffProduct product = offOpt.get();
    Double cal100 = product.getCalories100g();
    if (cal100 == null || cal100 < 0) {
      log.info("Product found on OFF for barcode {} but lacks calorie data", cleanBarcode);
      recordMiss(cleanBarcode, now);
      throw new BusinessException(ErrorCode.BARCODE_NOT_FOUND);
    }

    // Chuẩn hóa và lưu tự động vào bảng foods với source = OFF
    Food food = mapOffProductToFood(product, cleanBarcode, cal100);
    Food savedFood = foodRepository.save(food);

    return FoodResponse.fromEntity(savedFood);
  }

  private void recordMiss(String barcode, Instant now) {
    try {
      barcodeMissRepository.save(new BarcodeMiss(barcode, now.plus(7, ChronoUnit.DAYS)));
    } catch (Exception ex) {
      log.warn("Failed to record barcode miss for {}: {}", barcode, ex.getMessage());
    }
  }

  private Food mapOffProductToFood(
      OpenFoodFactsClient.OffProduct product, String barcode, double cal100) {
    Food food = new Food();
    String name = product.resolveDisplayName();
    food.setName(name);
    food.setNameUnaccented(unaccent(name));
    food.setCategory(FoodCategory.SNACK);
    food.setBarcode(barcode);
    food.setSource(FoodSource.OFF);
    food.setImageUrl(product.imageFrontUrl());
    food.setVerified(true);
    food.setOwner(null);

    double servingGrams = 100.0;
    String servingName = "1 phần (100g)";
    if (product.servingQuantity() != null && product.servingQuantity() > 0) {
      servingGrams = product.servingQuantity();
      servingName =
          (product.servingSize() != null && !product.servingSize().isBlank())
              ? product.servingSize().trim()
              : String.format("1 phần (%.0fg)", servingGrams);
    } else if (product.servingSize() != null && !product.servingSize().isBlank()) {
      servingName = product.servingSize().trim();
    }

    food.setDefaultServingName(servingName);
    food.setDefaultServingGrams(BigDecimal.valueOf(servingGrams).setScale(1, RoundingMode.HALF_UP));

    BigDecimal cal100Bd = BigDecimal.valueOf(cal100).setScale(1, RoundingMode.HALF_UP);
    food.setCaloriesPer100g(cal100Bd);

    double protein100 = product.getProtein100g() != null ? product.getProtein100g() : 0.0;
    double carbs100 = product.getCarbs100g() != null ? product.getCarbs100g() : 0.0;
    double fat100 = product.getFat100g() != null ? product.getFat100g() : 0.0;
    double sugar100 = product.getSugar100g() != null ? product.getSugar100g() : 0.0;
    double fiber100 = product.getFiber100g() != null ? product.getFiber100g() : 0.0;

    food.setProteinPer100g(BigDecimal.valueOf(protein100).setScale(1, RoundingMode.HALF_UP));
    food.setCarbsPer100g(BigDecimal.valueOf(carbs100).setScale(1, RoundingMode.HALF_UP));
    food.setFatPer100g(BigDecimal.valueOf(fat100).setScale(1, RoundingMode.HALF_UP));
    food.setSugarPer100g(BigDecimal.valueOf(sugar100).setScale(1, RoundingMode.HALF_UP));
    food.setFiberPer100g(BigDecimal.valueOf(fiber100).setScale(1, RoundingMode.HALF_UP));

    double ratio = servingGrams / 100.0;
    food.setCaloriesPerServing((int) Math.round(cal100 * ratio));
    food.setProteinPerServing(
        BigDecimal.valueOf(protein100 * ratio).setScale(1, RoundingMode.HALF_UP));
    food.setCarbsPerServing(
        BigDecimal.valueOf(carbs100 * ratio).setScale(1, RoundingMode.HALF_UP));
    food.setFatPerServing(BigDecimal.valueOf(fat100 * ratio).setScale(1, RoundingMode.HALF_UP));
    food.setSugarPerServing(
        BigDecimal.valueOf(sugar100 * ratio).setScale(1, RoundingMode.HALF_UP));
    food.setFiberPerServing(
        BigDecimal.valueOf(fiber100 * ratio).setScale(1, RoundingMode.HALF_UP));

    List<String> allergens = new ArrayList<>();
    if (product.allergensTags() != null) {
      for (String tag : product.allergensTags()) {
        allergens.add(tag.replace("en:", "").toUpperCase().trim());
      }
    }
    food.setAllergens(allergens);
    food.setSuitableMealTypes(List.of(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER, MealType.SNACK));

    return food;
  }

  public static String unaccent(String src) {
    if (src == null) {
      return "";
    }
    String normalized = Normalizer.normalize(src, Normalizer.Form.NFD);
    return Pattern.compile("\\p{InCombiningDiacriticalMarks}+")
        .matcher(normalized)
        .replaceAll("")
        .replace('đ', 'd')
        .replace('Đ', 'd')
        .toLowerCase()
        .trim();
  }
}
