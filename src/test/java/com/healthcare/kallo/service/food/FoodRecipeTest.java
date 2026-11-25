package com.healthcare.kallo.service.food;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.dto.request.food.CustomIngredientRequest;
import com.healthcare.kallo.dto.request.food.RecipeFoodRequest;
import com.healthcare.kallo.dto.request.food.RecipeItemRequest;
import com.healthcare.kallo.dto.response.food.FoodResponse;
import com.healthcare.kallo.model.food.Food;
import com.healthcare.kallo.model.food.FoodIngredient;
import com.healthcare.kallo.model.food.Ingredient;
import com.healthcare.kallo.model.food.enums.FoodCategory;
import com.healthcare.kallo.model.food.enums.FoodSource;
import com.healthcare.kallo.repository.food.FoodIngredientRepository;
import com.healthcare.kallo.repository.food.FoodRepository;
import com.healthcare.kallo.repository.food.IngredientRepository;
import com.healthcare.kallo.service.food.impl.FoodServiceImpl;
import com.healthcare.kallo.service.food.impl.IngredientServiceImpl;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FoodRecipeTest {

  @Mock private FoodRepository foodRepository;
  @Mock private IngredientRepository ingredientRepository;
  @Mock private FoodIngredientRepository foodIngredientRepository;

  private final FoodNutritionCalculator nutritionCalculator = new FoodNutritionCalculator();
  private FoodServiceImpl foodService;
  private IngredientServiceImpl ingredientService;

  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    foodService = new FoodServiceImpl(foodRepository, ingredientRepository, foodIngredientRepository, nutritionCalculator);
    ingredientService = new IngredientServiceImpl(ingredientRepository, foodRepository, nutritionCalculator);
  }

  @Test
  @DisplayName("testCreateRecipeWithCustomIngredient: tính toán chính xác dinh dưỡng snapshot khi ghép món từ nhiều nguyên liệu")
  void testCreateRecipeWithCustomIngredient() {
    // Ingredient 1: Yến mạch (System) 100g có 389 kcal, 16.9g pro, 66.3g carb, 6.9g fat
    UUID ing1Id = UUID.randomUUID();
    Ingredient ing1 = new Ingredient();
    ing1.setId(ing1Id);
    ing1.setName("Yến mạch");
    ing1.setCaloriesPer100g(new BigDecimal("389.0"));
    ing1.setProteinPer100g(new BigDecimal("16.9"));
    ing1.setCarbsPer100g(new BigDecimal("66.3"));
    ing1.setFatPer100g(new BigDecimal("6.9"));
    ing1.setSugarPer100g(BigDecimal.ZERO);
    ing1.setFiberPer100g(new BigDecimal("10.0"));
    ing1.setSource(FoodSource.SYSTEM);
    ing1.setActive(true);

    // Ingredient 2: Sữa hạnh nhân tự làm (User Custom) 100g có 50 kcal, 2.0g pro, 3.0g carb, 3.0g fat
    UUID ing2Id = UUID.randomUUID();
    Ingredient ing2 = new Ingredient();
    ing2.setId(ing2Id);
    ing2.setName("Sữa hạnh nhân");
    ing2.setCaloriesPer100g(new BigDecimal("50.0"));
    ing2.setProteinPer100g(new BigDecimal("2.0"));
    ing2.setCarbsPer100g(new BigDecimal("3.0"));
    ing2.setFatPer100g(new BigDecimal("3.0"));
    ing2.setSugarPer100g(BigDecimal.ONE);
    ing2.setFiberPer100g(BigDecimal.ZERO);
    ing2.setSource(FoodSource.USER_CUSTOM);
    ing2.setOwnerUserId(userId);
    ing2.setActive(true);

    when(ingredientRepository.findById(ing1Id)).thenReturn(Optional.of(ing1));
    when(ingredientRepository.findById(ing2Id)).thenReturn(Optional.of(ing2));

    when(foodRepository.save(any(Food.class))).thenAnswer(invocation -> {
      Food f = invocation.getArgument(0);
      f.setId(UUID.randomUUID());
      return f;
    });

    // Recipe: 50g yến mạch + 150g sữa hạnh nhân = 200g
    // Yến mạch (50g): 194.5 kcal, 8.45g pro, 33.15g carb, 3.45g fat
    // Sữa (150g): 75 kcal, 3.0g pro, 4.5g carb, 4.5g fat
    // Tổng: 269.5 kcal, 11.45g pro, 37.65g carb, 7.95g fat trên 200g
    // Trên 100g: 134.8 kcal, 5.7g pro, 18.8g carb, 4.0g fat
    RecipeFoodRequest request = new RecipeFoodRequest(
        "Bát Yến Mạch Hạnh Nhân",
        FoodCategory.STAPLE,
        "1 tô (200g)",
        List.of(
            new RecipeItemRequest(ing1Id, new BigDecimal("50.0")),
            new RecipeItemRequest(ing2Id, new BigDecimal("150.0"))
        ),
        List.of("BREAKFAST"),
        null
    );

    FoodResponse response = foodService.createRecipeFood(userId, request);

    assertThat(response).isNotNull();
    assertThat(response.name()).isEqualTo("Bát Yến Mạch Hạnh Nhân");
    assertThat(response.source()).isEqualTo(FoodSource.USER_CUSTOM);
    assertThat(response.ownerUserId()).isEqualTo(userId);
    assertThat(response.defaultServingGrams()).isEqualByComparingTo("200.0");
    assertThat(response.caloriesPer100g()).isEqualByComparingTo("134.8");
    assertThat(response.caloriesPerServing()).isEqualTo(270);
  }

  @Test
  @DisplayName("testPreventIDOROnIngredients: User A không thể dùng nguyên liệu custom của User B để tạo recipe")
  void testPreventIDOROnIngredients() {
    UUID otherUserId = UUID.randomUUID();
    UUID ingId = UUID.randomUUID();
    Ingredient ing = new Ingredient();
    ing.setId(ingId);
    ing.setName("Nguyên liệu của người khác");
    ing.setSource(FoodSource.USER_CUSTOM);
    ing.setOwnerUserId(otherUserId);
    ing.setActive(true);

    when(ingredientRepository.findById(ingId)).thenReturn(Optional.of(ing));

    RecipeFoodRequest request = new RecipeFoodRequest(
        "Món vi phạm",
        FoodCategory.STAPLE,
        "1 phần",
        List.of(new RecipeItemRequest(ingId, new BigDecimal("100.0"))),
        List.of("LUNCH"),
        null
    );

    assertThatThrownBy(() -> foodService.createRecipeFood(userId, request))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.FORBIDDEN_INGREDIENT_ACCESS);
  }

  @Test
  @DisplayName("testRecalculateRecipeOnIngredientUpdate: khi sửa nguyên liệu custom, các món recipe liên quan được tính lại snapshot")
  void testRecalculateRecipeOnIngredientUpdate() {
    UUID ingId = UUID.randomUUID();
    Ingredient ing = new Ingredient();
    ing.setId(ingId);
    ing.setName("Sốt tỏi");
    ing.setCaloriesPer100g(new BigDecimal("100.0"));
    ing.setProteinPer100g(new BigDecimal("2.0"));
    ing.setCarbsPer100g(new BigDecimal("10.0"));
    ing.setFatPer100g(new BigDecimal("5.0"));
    ing.setSource(FoodSource.USER_CUSTOM);
    ing.setOwnerUserId(userId);
    ing.setActive(true);

    when(ingredientRepository.findById(ingId)).thenReturn(Optional.of(ing));
    when(ingredientRepository.save(any(Ingredient.class))).thenReturn(ing);

    // Món recipe chứa 100g nguyên liệu này
    Food recipeFood = new Food();
    recipeFood.setId(UUID.randomUUID());
    recipeFood.setName("Bánh mì sốt tỏi");
    recipeFood.setDefaultServingGrams(new BigDecimal("100.0"));
    recipeFood.setOwnerUserId(userId);
    recipeFood.setSource(FoodSource.USER_CUSTOM);

    FoodIngredient fi = new FoodIngredient(recipeFood, ing, new BigDecimal("100.0"));
    recipeFood.setIngredients(new ArrayList<>(List.of(fi)));

    when(foodRepository.findAllCustomRecipesByIngredientAndOwner(ingId, userId))
        .thenReturn(List.of(recipeFood));

    // Cập nhật sốt tỏi từ 100 calo lên 300 calo
    CustomIngredientRequest updateReq = new CustomIngredientRequest(
        "Sốt tỏi siêu béo",
        new BigDecimal("300.0"),
        new BigDecimal("2.0"),
        new BigDecimal("10.0"),
        new BigDecimal("25.0"),
        BigDecimal.ZERO,
        BigDecimal.ZERO
    );

    ingredientService.updateCustomIngredient(userId, ingId, updateReq);

    // Snapshot của món recipe phải được cập nhật lại thành 300 calo
    assertThat(recipeFood.getCaloriesPer100g()).isEqualByComparingTo("300.0");
    assertThat(recipeFood.getCaloriesPerServing()).isEqualTo(300);
    verify(foodRepository).save(recipeFood);
  }

  @Test
  @DisplayName("testSoftDeleteCustomFood: xóa món tùy chỉnh áp dụng deleted_at")
  void testSoftDeleteCustomFood() {
    UUID foodId = UUID.randomUUID();
    Food food = new Food();
    food.setId(foodId);
    food.setName("Salad cá nhân");
    food.setSource(FoodSource.USER_CUSTOM);
    food.setOwnerUserId(userId);

    when(foodRepository.findById(foodId)).thenReturn(Optional.of(food));

    foodService.deleteCustomFood(userId, foodId);

    assertThat(food.getDeletedAt()).isNotNull();
    verify(foodRepository).save(food);
  }

  @Test
  @DisplayName("testPreventModifyingSystemFood: không cho phép sửa/xóa món ăn hệ thống")
  void testPreventModifyingSystemFood() {
    UUID foodId = UUID.randomUUID();
    Food food = new Food();
    food.setId(foodId);
    food.setName("Phở bò");
    food.setSource(FoodSource.SYSTEM);
    food.setOwnerUserId(null);

    when(foodRepository.findById(foodId)).thenReturn(Optional.of(food));

    assertThatThrownBy(() -> foodService.deleteCustomFood(userId, foodId))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.CANNOT_MODIFY_SYSTEM_FOOD);
  }
}
