package com.healthcare.kallo.service.food.impl;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.dto.response.food.FoodResponse;
import com.healthcare.kallo.model.food.BarcodeMiss;
import com.healthcare.kallo.model.food.Food;
import com.healthcare.kallo.model.food.enums.FoodCategory;
import com.healthcare.kallo.model.food.enums.FoodSource;
import com.healthcare.kallo.repository.food.BarcodeMissRepository;
import com.healthcare.kallo.repository.food.FoodRepository;
import com.healthcare.kallo.service.food.BarcodeService;
import com.healthcare.kallo.service.food.FoodNutritionCalculator;
import com.healthcare.kallo.service.food.client.OpenFoodFactsClient;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class BarcodeServiceImpl implements BarcodeService {

  private final FoodRepository foodRepository;
  private final BarcodeMissRepository barcodeMissRepository;
  private final OpenFoodFactsClient openFoodFactsClient;
  private final FoodServiceImpl foodService;

  public BarcodeServiceImpl(
      FoodRepository foodRepository,
      BarcodeMissRepository barcodeMissRepository,
      OpenFoodFactsClient openFoodFactsClient,
      FoodServiceImpl foodService) {
    this.foodRepository = foodRepository;
    this.barcodeMissRepository = barcodeMissRepository;
    this.openFoodFactsClient = openFoodFactsClient;
    this.foodService = foodService;
  }

  @Override
  @Transactional
  public FoodResponse getFoodByBarcode(UUID currentUserId, String barcode) {
    if (barcode == null || barcode.trim().isEmpty() || !barcode.trim().matches("^[0-9A-Za-z-]{3,64}$")) {
      throw new BusinessException(ErrorCode.INVALID_BARCODE);
    }
    String normalizedBarcode = barcode.trim();

    // 1. Local Cache Hit (DB lookup)
    Optional<Food> localFood = foodRepository.findByBarcodeAndDeletedAtIsNull(normalizedBarcode);
    if (localFood.isPresent()) {
      return foodService.mapToFoodResponse(localFood.get());
    }

    // 2. Check active miss cache
    Optional<BarcodeMiss> activeMiss = barcodeMissRepository.findActiveMiss(normalizedBarcode, Instant.now());
    if (activeMiss.isPresent()) {
      throw new BusinessException(ErrorCode.BARCODE_NOT_FOUND);
    }

    // 3. Read-Through to Open Food Facts
    Optional<OpenFoodFactsClient.OffProductDto> offProduct = openFoodFactsClient.fetchProduct(normalizedBarcode);
    if (offProduct.isEmpty()) {
      // Record cache miss for 7 days
      try {
        barcodeMissRepository.save(new BarcodeMiss(normalizedBarcode, Instant.now().plus(7, ChronoUnit.DAYS)));
      } catch (Exception e) {
        log.warn("Could not save barcode miss for {}: {}", normalizedBarcode, e.getMessage());
      }
      throw new BusinessException(ErrorCode.BARCODE_NOT_FOUND);
    }

    // Save newly discovered OFF product
    OpenFoodFactsClient.OffProductDto dto = offProduct.get();
    Food food = new Food();
    food.setName(dto.name());
    food.setCategory(FoodCategory.OTHER);
    food.setDefaultServingName(dto.defaultServingName());
    food.setDefaultServingGrams(dto.defaultServingGrams());
    food.setCaloriesPer100g(dto.caloriesPer100g());
    food.setProteinPer100g(dto.proteinPer100g());
    food.setCarbsPer100g(dto.carbsPer100g());
    food.setFatPer100g(dto.fatPer100g());
    food.setSugarPer100g(dto.sugarPer100g());
    food.setFiberPer100g(dto.fiberPer100g());

    BigDecimal factor = dto.defaultServingGrams().divide(FoodNutritionCalculator.HUNDRED, 4, RoundingMode.HALF_UP);
    food.setCaloriesPerServing(dto.caloriesPer100g().multiply(factor).setScale(0, RoundingMode.HALF_UP).intValue());
    food.setProteinPerServing(dto.proteinPer100g().multiply(factor).setScale(1, RoundingMode.HALF_UP));
    food.setCarbsPerServing(dto.carbsPer100g().multiply(factor).setScale(1, RoundingMode.HALF_UP));
    food.setFatPerServing(dto.fatPer100g().multiply(factor).setScale(1, RoundingMode.HALF_UP));
    food.setSugarPerServing(dto.sugarPer100g().multiply(factor).setScale(1, RoundingMode.HALF_UP));
    food.setFiberPerServing(dto.fiberPer100g().multiply(factor).setScale(1, RoundingMode.HALF_UP));

    food.setImageUrl(dto.imageUrl());
    food.setBarcode(normalizedBarcode);
    food.setSource(FoodSource.OFF);
    food.setVerified(true);
    food.setSuitableMealTypes(List.of("BREAKFAST", "LUNCH", "DINNER", "SNACK"));

    Food saved = foodRepository.save(food);
    return foodService.mapToFoodResponse(saved);
  }
}
