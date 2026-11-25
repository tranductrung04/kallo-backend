package com.healthcare.kallo.service.food.impl;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.common.PageResponse;
import com.healthcare.kallo.dto.request.food.CustomFoodRequest;
import com.healthcare.kallo.dto.request.food.RecipeFoodRequest;
import com.healthcare.kallo.dto.request.food.RecipeItemRequest;
import com.healthcare.kallo.dto.response.food.FoodIngredientItemResponse;
import com.healthcare.kallo.dto.response.food.FoodResponse;
import com.healthcare.kallo.dto.response.food.FoodSearchItemResponse;
import com.healthcare.kallo.model.food.Food;
import com.healthcare.kallo.model.food.FoodIngredient;
import com.healthcare.kallo.model.food.Ingredient;
import com.healthcare.kallo.model.food.enums.FoodCategory;
import com.healthcare.kallo.model.food.enums.FoodSource;
import com.healthcare.kallo.repository.food.FoodIngredientRepository;
import com.healthcare.kallo.repository.food.FoodRepository;
import com.healthcare.kallo.repository.food.IngredientRepository;
import com.healthcare.kallo.service.food.FoodNutritionCalculator;
import com.healthcare.kallo.service.food.FoodService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FoodServiceImpl implements FoodService {

  private final FoodRepository foodRepository;
  private final IngredientRepository ingredientRepository;
  private final FoodIngredientRepository foodIngredientRepository;
  private final FoodNutritionCalculator nutritionCalculator;

  public FoodServiceImpl(
      FoodRepository foodRepository,
      IngredientRepository ingredientRepository,
      FoodIngredientRepository foodIngredientRepository,
      FoodNutritionCalculator nutritionCalculator) {
    this.foodRepository = foodRepository;
    this.ingredientRepository = ingredientRepository;
    this.foodIngredientRepository = foodIngredientRepository;
    this.nutritionCalculator = nutritionCalculator;
  }

  @Override
  @Transactional(readOnly = true)
  public PageResponse<FoodSearchItemResponse> searchFoods(
      UUID currentUserId, String q, FoodCategory category, int page, int size) {
    if (page < 0) page = 0;
    if (size <= 0 || size > 100) size = 20;

    String catStr = category != null ? category.name() : null;
    Page<Food> foodPage = foodRepository.searchFoods(currentUserId, q, catStr, PageRequest.of(page, size));

    List<FoodSearchItemResponse> items = foodPage.getContent().stream()
        .map(f -> new FoodSearchItemResponse(
            f.getId(),
            f.getName(),
            f.getCategory(),
            f.getDefaultServingName(),
            f.getDefaultServingGrams(),
            f.getCaloriesPerServing(),
            f.getProteinPerServing(),
            f.getCarbsPerServing(),
            f.getFatPerServing(),
            f.getImageUrl(),
            f.getSource(),
            f.isVerified()
        ))
        .toList();

    return new PageResponse<>(items, foodPage.getNumber(), foodPage.getSize(), foodPage.getTotalElements(), foodPage.getTotalPages());
  }

  @Override
  @Transactional(readOnly = true)
  public FoodResponse getFoodById(UUID currentUserId, UUID foodId) {
    Food food = foodRepository.findByIdWithIngredients(foodId)
        .orElseThrow(() -> new BusinessException(ErrorCode.FOOD_NOT_FOUND));

    if (food.getDeletedAt() != null) {
      throw new BusinessException(ErrorCode.FOOD_NOT_FOUND);
    }

    if (food.getOwnerUserId() != null && !food.getOwnerUserId().equals(currentUserId)) {
      throw new BusinessException(ErrorCode.FORBIDDEN_FOOD_ACCESS);
    }

    return mapToFoodResponse(food);
  }

  @Override
  @Transactional
  public FoodResponse createCustomFood(UUID currentUserId, CustomFoodRequest request) {
    Food food = new Food();
    food.setName(request.name().trim());
    food.setCategory(request.category());
    food.setDefaultServingName(request.defaultServingName().trim());
    food.setDefaultServingGrams(request.defaultServingGrams());

    food.setCaloriesPer100g(request.caloriesPer100g());
    food.setProteinPer100g(request.proteinPer100g());
    food.setCarbsPer100g(request.carbsPer100g());
    food.setFatPer100g(request.fatPer100g());
    food.setSugarPer100g(request.sugarPer100g());
    food.setFiberPer100g(request.fiberPer100g());

    BigDecimal factor = request.defaultServingGrams().divide(FoodNutritionCalculator.HUNDRED, 4, RoundingMode.HALF_UP);
    food.setCaloriesPerServing(request.caloriesPer100g().multiply(factor).setScale(0, RoundingMode.HALF_UP).intValue());
    food.setProteinPerServing(request.proteinPer100g().multiply(factor).setScale(1, RoundingMode.HALF_UP));
    food.setCarbsPerServing(request.carbsPer100g().multiply(factor).setScale(1, RoundingMode.HALF_UP));
    food.setFatPerServing(request.fatPer100g().multiply(factor).setScale(1, RoundingMode.HALF_UP));
    food.setSugarPerServing(request.sugarPer100g().multiply(factor).setScale(1, RoundingMode.HALF_UP));
    food.setFiberPerServing(request.fiberPer100g().multiply(factor).setScale(1, RoundingMode.HALF_UP));

    food.setImageUrl(request.imageUrl());
    food.setSuitableMealTypes(request.suitableMealTypes());
    food.setSource(FoodSource.USER_CUSTOM);
    food.setOwnerUserId(currentUserId);
    food.setVerified(true);

    Food saved = foodRepository.save(food);
    return mapToFoodResponse(saved);
  }

  @Override
  @Transactional
  public FoodResponse createRecipeFood(UUID currentUserId, RecipeFoodRequest request) {
    // 1. Validate IDOR & existence of ingredients
    List<FoodIngredient> foodIngredients = new ArrayList<>();
    Food food = new Food();
    food.setName(request.name().trim());
    food.setCategory(request.category());
    food.setDefaultServingName(request.defaultServingName().trim());
    food.setImageUrl(request.imageUrl());
    food.setSuitableMealTypes(request.suitableMealTypes());
    food.setSource(FoodSource.USER_CUSTOM);
    food.setOwnerUserId(currentUserId);
    food.setVerified(true);

    for (RecipeItemRequest item : request.items()) {
      Ingredient ingredient = ingredientRepository.findById(item.ingredientId())
          .orElseThrow(() -> new BusinessException(ErrorCode.INGREDIENT_NOT_FOUND));

      if (!ingredient.isActive()) {
        throw new BusinessException(ErrorCode.INGREDIENT_NOT_FOUND);
      }

      // Must be SYSTEM or owned by current user
      if (ingredient.getOwnerUserId() != null && !ingredient.getOwnerUserId().equals(currentUserId)) {
        throw new BusinessException(ErrorCode.FORBIDDEN_INGREDIENT_ACCESS);
      }

      FoodIngredient fi = new FoodIngredient(food, ingredient, item.quantityGrams());
      foodIngredients.add(fi);
    }

    // 2. Compute nutrition snapshot
    nutritionCalculator.recalculateNutritionSnapshot(food, foodIngredients);

    // 3. Save food and cascade food ingredients
    Food savedFood = foodRepository.save(food);
    for (FoodIngredient fi : foodIngredients) {
      fi.setFood(savedFood);
      fi.getId().setFoodId(savedFood.getId());
    }
    foodIngredientRepository.saveAll(foodIngredients);
    savedFood.setIngredients(foodIngredients);

    return mapToFoodResponse(savedFood);
  }

  @Override
  @Transactional
  public FoodResponse updateCustomFood(UUID currentUserId, UUID foodId, CustomFoodRequest request) {
    Food food = foodRepository.findByIdWithIngredients(foodId)
        .orElseThrow(() -> new BusinessException(ErrorCode.FOOD_NOT_FOUND));

    if (food.getSource() == FoodSource.SYSTEM) {
      throw new BusinessException(ErrorCode.CANNOT_MODIFY_SYSTEM_FOOD);
    }

    if (!currentUserId.equals(food.getOwnerUserId())) {
      throw new BusinessException(ErrorCode.FORBIDDEN_FOOD_ACCESS);
    }

    food.setName(request.name().trim());
    food.setCategory(request.category());
    food.setDefaultServingName(request.defaultServingName().trim());
    food.setDefaultServingGrams(request.defaultServingGrams());

    food.setCaloriesPer100g(request.caloriesPer100g());
    food.setProteinPer100g(request.proteinPer100g());
    food.setCarbsPer100g(request.carbsPer100g());
    food.setFatPer100g(request.fatPer100g());
    food.setSugarPer100g(request.sugarPer100g());
    food.setFiberPer100g(request.fiberPer100g());

    BigDecimal factor = request.defaultServingGrams().divide(FoodNutritionCalculator.HUNDRED, 4, RoundingMode.HALF_UP);
    food.setCaloriesPerServing(request.caloriesPer100g().multiply(factor).setScale(0, RoundingMode.HALF_UP).intValue());
    food.setProteinPerServing(request.proteinPer100g().multiply(factor).setScale(1, RoundingMode.HALF_UP));
    food.setCarbsPerServing(request.carbsPer100g().multiply(factor).setScale(1, RoundingMode.HALF_UP));
    food.setFatPerServing(request.fatPer100g().multiply(factor).setScale(1, RoundingMode.HALF_UP));
    food.setSugarPerServing(request.sugarPer100g().multiply(factor).setScale(1, RoundingMode.HALF_UP));
    food.setFiberPerServing(request.fiberPer100g().multiply(factor).setScale(1, RoundingMode.HALF_UP));

    food.setImageUrl(request.imageUrl());
    food.setSuitableMealTypes(request.suitableMealTypes());

    Food saved = foodRepository.save(food);
    return mapToFoodResponse(saved);
  }

  @Override
  @Transactional
  public void deleteCustomFood(UUID currentUserId, UUID foodId) {
    Food food = foodRepository.findById(foodId)
        .orElseThrow(() -> new BusinessException(ErrorCode.FOOD_NOT_FOUND));

    if (food.getSource() == FoodSource.SYSTEM) {
      throw new BusinessException(ErrorCode.CANNOT_MODIFY_SYSTEM_FOOD);
    }

    if (!currentUserId.equals(food.getOwnerUserId())) {
      throw new BusinessException(ErrorCode.FORBIDDEN_FOOD_ACCESS);
    }

    // Soft delete
    food.setDeletedAt(Instant.now());
    foodRepository.save(food);
  }

  public FoodResponse mapToFoodResponse(Food f) {
    List<FoodIngredientItemResponse> ingItems = new ArrayList<>();
    List<String> allergenNames = new ArrayList<>();

    if (f.getIngredients() != null) {
      for (FoodIngredient fi : f.getIngredients()) {
        Ingredient ing = fi.getIngredient();
        if (ing != null) {
          ingItems.add(new FoodIngredientItemResponse(
              ing.getId(),
              ing.getName(),
              fi.getQuantityGrams(),
              ing.isAllergen(),
              ing.getCaloriesPer100g()
          ));
          if (ing.isAllergen()) {
            allergenNames.add(ing.getName());
          }
        }
      }
    }

    return new FoodResponse(
        f.getId(),
        f.getName(),
        f.getCategory(),
        f.getDefaultServingName(),
        f.getDefaultServingGrams(),
        f.getCaloriesPer100g(),
        f.getProteinPer100g(),
        f.getCarbsPer100g(),
        f.getFatPer100g(),
        f.getSugarPer100g(),
        f.getFiberPer100g(),
        f.getCaloriesPerServing(),
        f.getProteinPerServing(),
        f.getCarbsPerServing(),
        f.getFatPerServing(),
        f.getSugarPerServing(),
        f.getFiberPerServing(),
        f.getImageUrl(),
        f.getBarcode(),
        f.getSource(),
        f.getVietfoodClassId(),
        f.getSuitableMealTypes(),
        f.getReferencePriceVnd(),
        f.isVerified(),
        f.getOwnerUserId(),
        ingItems,
        allergenNames,
        f.getCreatedAt(),
        f.getUpdatedAt()
    );
  }
}
