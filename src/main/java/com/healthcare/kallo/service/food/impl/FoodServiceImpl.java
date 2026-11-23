package com.healthcare.kallo.service.food.impl;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.common.PageResponse;
import com.healthcare.kallo.dto.request.food.CustomFoodRequest;
import com.healthcare.kallo.dto.response.food.FoodResponse;
import com.healthcare.kallo.dto.response.food.FoodSearchItemResponse;
import com.healthcare.kallo.model.auth.User;
import com.healthcare.kallo.model.food.Food;
import com.healthcare.kallo.model.food.enums.FoodCategory;
import com.healthcare.kallo.model.food.enums.FoodSource;
import com.healthcare.kallo.model.food.enums.MealType;
import com.healthcare.kallo.repository.auth.UserRepository;
import com.healthcare.kallo.repository.food.FoodRepository;
import com.healthcare.kallo.repository.nutri.UserFoodLogRepository;
import com.healthcare.kallo.repository.plan.MealPlanFoodRepository;
import com.healthcare.kallo.service.food.FoodService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FoodServiceImpl implements FoodService {

  private final FoodRepository foodRepository;
  private final UserRepository userRepository;
  private final UserFoodLogRepository userFoodLogRepository;
  private final MealPlanFoodRepository mealPlanFoodRepository;

  public FoodServiceImpl(
      FoodRepository foodRepository,
      UserRepository userRepository,
      UserFoodLogRepository userFoodLogRepository,
      MealPlanFoodRepository mealPlanFoodRepository) {
    this.foodRepository = foodRepository;
    this.userRepository = userRepository;
    this.userFoodLogRepository = userFoodLogRepository;
    this.mealPlanFoodRepository = mealPlanFoodRepository;
  }

  public FoodServiceImpl(FoodRepository foodRepository, UserRepository userRepository) {
    this(foodRepository, userRepository, null, null);
  }

  @Override
  @Transactional(readOnly = true)
  public PageResponse<FoodSearchItemResponse> searchFoods(
      UUID currentUserId, String query, FoodCategory category, Pageable pageable) {
    String cleanQuery = (query != null && !query.trim().isBlank()) ? query.trim() : null;
    String categoryName = (category != null) ? category.name() : null;

    Page<Food> page = foodRepository.searchFoods(currentUserId, cleanQuery, categoryName, pageable);
    List<FoodSearchItemResponse> items =
        page.getContent().stream().map(FoodSearchItemResponse::fromEntity).toList();

    return new PageResponse<>(
        items, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
  }

  @Override
  @Transactional(readOnly = true)
  public FoodResponse getFoodById(UUID foodId, UUID currentUserId) {
    Food food =
        foodRepository
            .findVisibleFoodById(foodId, currentUserId)
            .orElseThrow(() -> new BusinessException(ErrorCode.FOOD_NOT_FOUND));

    return FoodResponse.fromEntity(food);
  }

  @Override
  @Transactional(readOnly = true)
  public FoodResponse getFoodByVietfoodClassId(int classId) {
    Food food =
        foodRepository
            .findByVietfoodClassId(classId)
            .orElseThrow(() -> new BusinessException(ErrorCode.FOOD_NOT_FOUND));

    if (food.getCategory() == FoodCategory.NON_FOOD) {
      throw new BusinessException(ErrorCode.NON_FOOD_DETECTED);
    }

    return FoodResponse.fromEntity(food);
  }

  @Override
  @Transactional
  public FoodResponse createCustomFood(UUID currentUserId, CustomFoodRequest request) {
    User user =
        userRepository
            .findById(currentUserId)
            .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

    Food food = new Food();
    food.setOwner(user);
    food.setSource(FoodSource.USER_CUSTOM);
    food.setVerified(false);

    applyRequestToFood(food, request);

    Food savedFood = foodRepository.save(food);
    return FoodResponse.fromEntity(savedFood);
  }

  @Override
  @Transactional
  public FoodResponse updateCustomFood(
      UUID currentUserId, UUID foodId, CustomFoodRequest request) {
    Food food =
        foodRepository
            .findById(foodId)
            .orElseThrow(() -> new BusinessException(ErrorCode.FOOD_NOT_FOUND));

    if (food.getOwner() == null) {
      throw new BusinessException(ErrorCode.CANNOT_MODIFY_SYSTEM_FOOD);
    }

    if (!food.getOwner().getId().equals(currentUserId)) {
      throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    applyRequestToFood(food, request);

    Food savedFood = foodRepository.save(food);
    return FoodResponse.fromEntity(savedFood);
  }

  @Override
  @Transactional
  public void deleteCustomFood(UUID currentUserId, UUID foodId) {
    Food food =
        foodRepository
            .findById(foodId)
            .orElseThrow(() -> new BusinessException(ErrorCode.FOOD_NOT_FOUND));

    if (food.getOwner() == null) {
      throw new BusinessException(ErrorCode.CANNOT_MODIFY_SYSTEM_FOOD);
    }

    if (!food.getOwner().getId().equals(currentUserId)) {
      throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    if (userFoodLogRepository != null && userFoodLogRepository.existsByFoodId(foodId)) {
      throw new BusinessException(ErrorCode.FOOD_IN_USE, "Món ăn đang được sử dụng trong nhật ký ăn uống");
    }

    if (mealPlanFoodRepository != null && mealPlanFoodRepository.existsByFoodId(foodId)) {
      throw new BusinessException(ErrorCode.FOOD_IN_USE, "Món ăn đang được sử dụng trong thực đơn");
    }

    foodRepository.delete(food);
  }

  private void applyRequestToFood(Food food, CustomFoodRequest request) {
    String name = request.name().trim();
    food.setName(name);
    food.setNameUnaccented(unaccent(name));
    food.setCategory(request.category());
    food.setDefaultServingName(request.defaultServingName().trim());

    BigDecimal servingGrams = request.defaultServingGrams().setScale(1, RoundingMode.HALF_UP);
    food.setDefaultServingGrams(servingGrams);

    BigDecimal cal100 = request.caloriesPer100g().setScale(1, RoundingMode.HALF_UP);
    BigDecimal pro100 = request.proteinPer100g().setScale(1, RoundingMode.HALF_UP);
    BigDecimal carb100 = request.carbsPer100g().setScale(1, RoundingMode.HALF_UP);
    BigDecimal fat100 = request.fatPer100g().setScale(1, RoundingMode.HALF_UP);

    BigDecimal sugar100 =
        (request.sugarPer100g() != null)
            ? request.sugarPer100g().setScale(1, RoundingMode.HALF_UP)
            : BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
    BigDecimal fiber100 =
        (request.fiberPer100g() != null)
            ? request.fiberPer100g().setScale(1, RoundingMode.HALF_UP)
            : BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);

    food.setCaloriesPer100g(cal100);
    food.setProteinPer100g(pro100);
    food.setCarbsPer100g(carb100);
    food.setFatPer100g(fat100);
    food.setSugarPer100g(sugar100);
    food.setFiberPer100g(fiber100);

    BigDecimal ratio = servingGrams.divide(BigDecimal.valueOf(100.0), 4, RoundingMode.HALF_UP);

    int calServing =
        cal100.multiply(ratio).setScale(0, RoundingMode.HALF_UP).intValue();
    BigDecimal proServing = pro100.multiply(ratio).setScale(1, RoundingMode.HALF_UP);
    BigDecimal carbServing = carb100.multiply(ratio).setScale(1, RoundingMode.HALF_UP);
    BigDecimal fatServing = fat100.multiply(ratio).setScale(1, RoundingMode.HALF_UP);
    BigDecimal sugarServing = sugar100.multiply(ratio).setScale(1, RoundingMode.HALF_UP);
    BigDecimal fiberServing = fiber100.multiply(ratio).setScale(1, RoundingMode.HALF_UP);

    food.setCaloriesPerServing(calServing);
    food.setProteinPerServing(proServing);
    food.setCarbsPerServing(carbServing);
    food.setFatPerServing(fatServing);
    food.setSugarPerServing(sugarServing);
    food.setFiberPerServing(fiberServing);

    food.setImageUrl(request.imageUrl());
    food.setBarcode(request.barcode());
    food.setAllergens(request.allergens() != null ? request.allergens() : List.of());
    food.setSuitableMealTypes(
        request.suitableMealTypes() != null
            ? request.suitableMealTypes()
            : List.of(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER, MealType.SNACK));
    food.setReferencePriceVnd(request.referencePriceVnd());
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
