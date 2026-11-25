package com.healthcare.kallo.service.food.impl;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.dto.request.food.CustomIngredientRequest;
import com.healthcare.kallo.dto.response.food.IngredientResponse;
import com.healthcare.kallo.model.food.Food;
import com.healthcare.kallo.model.food.Ingredient;
import com.healthcare.kallo.model.food.enums.FoodSource;
import com.healthcare.kallo.repository.food.FoodRepository;
import com.healthcare.kallo.repository.food.IngredientRepository;
import com.healthcare.kallo.service.food.FoodNutritionCalculator;
import com.healthcare.kallo.service.food.IngredientService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IngredientServiceImpl implements IngredientService {

  private static final int MAX_CUSTOM_INGREDIENTS_PER_DAY = 30;
  private static final BigDecimal MAX_MACROS_SUM = new BigDecimal("105.0");

  private final IngredientRepository ingredientRepository;
  private final FoodRepository foodRepository;
  private final FoodNutritionCalculator nutritionCalculator;

  public IngredientServiceImpl(
      IngredientRepository ingredientRepository,
      FoodRepository foodRepository,
      FoodNutritionCalculator nutritionCalculator) {
    this.ingredientRepository = ingredientRepository;
    this.foodRepository = foodRepository;
    this.nutritionCalculator = nutritionCalculator;
  }

  @Override
  @Transactional(readOnly = true)
  public List<IngredientResponse> searchIngredients(UUID currentUserId, String q, boolean allergenOnly) {
    List<Ingredient> list = ingredientRepository.searchIngredients(currentUserId, q, allergenOnly);
    return list.stream().map(this::mapToResponse).toList();
  }

  @Override
  @Transactional
  public IngredientResponse createCustomIngredient(UUID currentUserId, CustomIngredientRequest request) {
    validateMacros(request.proteinPer100g(), request.carbsPer100g(), request.fatPer100g());

    // Check rate limit: 30 items / user / day
    Instant oneDayAgo = Instant.now().minus(24, ChronoUnit.HOURS);
    long count = ingredientRepository.countByOwnerUserIdAndCreatedAtAfter(currentUserId, oneDayAgo);
    if (count >= MAX_CUSTOM_INGREDIENTS_PER_DAY) {
      throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
    }

    Ingredient ingredient = new Ingredient();
    ingredient.setName(request.name().trim());
    ingredient.setCaloriesPer100g(request.caloriesPer100g());
    ingredient.setProteinPer100g(request.proteinPer100g());
    ingredient.setCarbsPer100g(request.carbsPer100g());
    ingredient.setFatPer100g(request.fatPer100g());
    ingredient.setSugarPer100g(request.sugarPer100g());
    ingredient.setFiberPer100g(request.fiberPer100g());
    ingredient.setAllergen(false);
    ingredient.setSource(FoodSource.USER_CUSTOM);
    ingredient.setOwnerUserId(currentUserId);
    ingredient.setActive(true);

    Ingredient saved = ingredientRepository.save(ingredient);
    return mapToResponse(saved);
  }

  @Override
  @Transactional
  public IngredientResponse updateCustomIngredient(UUID currentUserId, UUID ingredientId, CustomIngredientRequest request) {
    Ingredient ingredient = ingredientRepository.findById(ingredientId)
        .orElseThrow(() -> new BusinessException(ErrorCode.INGREDIENT_NOT_FOUND));

    if (ingredient.getSource() == FoodSource.SYSTEM || ingredient.getOwnerUserId() == null) {
      throw new BusinessException(ErrorCode.CANNOT_MODIFY_SYSTEM_INGREDIENT);
    }

    if (!currentUserId.equals(ingredient.getOwnerUserId())) {
      throw new BusinessException(ErrorCode.FORBIDDEN_INGREDIENT_ACCESS);
    }

    validateMacros(request.proteinPer100g(), request.carbsPer100g(), request.fatPer100g());

    ingredient.setName(request.name().trim());
    ingredient.setCaloriesPer100g(request.caloriesPer100g());
    ingredient.setProteinPer100g(request.proteinPer100g());
    ingredient.setCarbsPer100g(request.carbsPer100g());
    ingredient.setFatPer100g(request.fatPer100g());
    ingredient.setSugarPer100g(request.sugarPer100g());
    ingredient.setFiberPer100g(request.fiberPer100g());
    Ingredient saved = ingredientRepository.save(ingredient);

    // Recalculate all affected custom recipes for this user
    List<Food> affectedFoods = foodRepository.findAllCustomRecipesByIngredientAndOwner(ingredientId, currentUserId);
    for (Food food : affectedFoods) {
      nutritionCalculator.recalculateNutritionSnapshot(food, food.getIngredients());
      foodRepository.save(food);
    }

    return mapToResponse(saved);
  }

  @Override
  @Transactional
  public void deleteCustomIngredient(UUID currentUserId, UUID ingredientId) {
    Ingredient ingredient = ingredientRepository.findById(ingredientId)
        .orElseThrow(() -> new BusinessException(ErrorCode.INGREDIENT_NOT_FOUND));

    if (ingredient.getSource() == FoodSource.SYSTEM || ingredient.getOwnerUserId() == null) {
      throw new BusinessException(ErrorCode.CANNOT_MODIFY_SYSTEM_INGREDIENT);
    }

    if (!currentUserId.equals(ingredient.getOwnerUserId())) {
      throw new BusinessException(ErrorCode.FORBIDDEN_INGREDIENT_ACCESS);
    }

    // Soft delete
    ingredient.setActive(false);
    ingredientRepository.save(ingredient);
  }

  private void validateMacros(BigDecimal pro, BigDecimal carb, BigDecimal fat) {
    BigDecimal sum = pro.add(carb).add(fat);
    if (sum.compareTo(MAX_MACROS_SUM) > 0) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR);
    }
  }

  public IngredientResponse mapToResponse(Ingredient ing) {
    return new IngredientResponse(
        ing.getId(),
        ing.getCode(),
        ing.getName(),
        ing.getCaloriesPer100g(),
        ing.getProteinPer100g(),
        ing.getCarbsPer100g(),
        ing.getFatPer100g(),
        ing.getSugarPer100g(),
        ing.getFiberPer100g(),
        ing.isAllergen(),
        ing.getSource(),
        ing.getOwnerUserId(),
        ing.isActive(),
        ing.getCreatedAt(),
        ing.getUpdatedAt()
    );
  }
}
