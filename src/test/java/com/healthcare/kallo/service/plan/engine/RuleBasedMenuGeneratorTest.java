package com.healthcare.kallo.service.plan.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.model.food.Food;
import com.healthcare.kallo.model.food.enums.FoodCategory;
import com.healthcare.kallo.model.food.enums.MealType;
import com.healthcare.kallo.model.plan.MealPlan;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RuleBasedMenuGeneratorTest {

  private VectorSimilarityEngine vectorEngine;
  private RuleBasedMenuGenerator generator;

  @BeforeEach
  void setUp() {
    vectorEngine = new VectorSimilarityEngine();
    generator = new RuleBasedMenuGenerator(vectorEngine);
  }

  private Food createFood(String name, int calories, List<MealType> mealTypes, List<String> allergens, BigDecimal sugar, BigDecimal fiber, List<Double> vector) {
    Food food = new Food();
    food.setId(UUID.randomUUID());
    food.setName(name);
    food.setNameUnaccented(name);
    food.setCategory(FoodCategory.RICE_DISH);
    food.setDefaultServingName("1 phần");
    food.setDefaultServingGrams(BigDecimal.valueOf(200));
    food.setCaloriesPerServing(calories);
    food.setCaloriesPer100g(BigDecimal.valueOf(calories / 2.0));
    food.setProteinPerServing(BigDecimal.valueOf(25.0));
    food.setProteinPer100g(BigDecimal.valueOf(12.5));
    food.setCarbsPerServing(BigDecimal.valueOf(40.0));
    food.setCarbsPer100g(BigDecimal.valueOf(20.0));
    food.setFatPerServing(BigDecimal.valueOf(10.0));
    food.setFatPer100g(BigDecimal.valueOf(5.0));
    food.setSugarPerServing(sugar != null ? sugar : BigDecimal.ZERO);
    food.setSugarPer100g(sugar != null ? sugar.divide(BigDecimal.valueOf(2)) : BigDecimal.ZERO);
    food.setFiberPerServing(fiber != null ? fiber : BigDecimal.ZERO);
    food.setFiberPer100g(fiber != null ? fiber.divide(BigDecimal.valueOf(2)) : BigDecimal.ZERO);
    food.setSuitableMealTypes(mealTypes != null ? mealTypes : List.of(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER));
    food.setAllergens(allergens != null ? allergens : List.of());
    food.setFeatureVector(vector != null ? vector : vectorEngine.defaultFoodFeatureVector());
    food.setReferencePriceVnd(35000);
    return food;
  }

  @Test
  @DisplayName("Generator respects allergies strictly")
  void generate_respectsAllergiesStrictly() {
    Food shrimpSoup = createFood("Canh tôm", 300, List.of(MealType.LUNCH), List.of("SEAFOOD", "SHRIMP"), BigDecimal.ONE, BigDecimal.ONE, null);
    Food beefRice = createFood("Cơm bò", 600, List.of(MealType.LUNCH, MealType.DINNER), List.of("BEEF"), BigDecimal.ONE, BigDecimal.ONE, null);
    Food oatmeal = createFood("Yến mạch", 400, List.of(MealType.BREAKFAST), List.of(), BigDecimal.ONE, BigDecimal.valueOf(5), null);
    Food yogurt = createFood("Sữa chua", 150, List.of(MealType.SNACK), List.of("DAIRY"), BigDecimal.valueOf(3), BigDecimal.ONE, null);

    List<Food> foods = List.of(shrimpSoup, beefRice, oatmeal, yogurt);

    RuleBasedMenuGenerator.PlanGenerationInput input = new RuleBasedMenuGenerator.PlanGenerationInput(
        1800,
        BigDecimal.valueOf(100),
        BigDecimal.valueOf(200),
        BigDecimal.valueOf(50),
        List.of("SHRIMP", "SEAFOOD"),
        List.of(),
        vectorEngine.defaultPreferenceVector(),
        null,
        Set.of(),
        foods,
        LocalDate.now(),
        42L
    );

    MealPlan plan = generator.generate(input);

    assertThat(plan.getItems()).isNotEmpty();
    assertThat(plan.getItems())
        .noneMatch(item -> item.getFood().getName().equals("Canh tôm"));
  }

  @Test
  @DisplayName("Generator filters high sugar food and sets medical warning for diabetics")
  void generate_diabeticUser_filtersHighSugarAndSetsWarning() {
    Food sweetCake = createFood("Bánh ngọt", 400, List.of(MealType.SNACK), List.of(), BigDecimal.valueOf(25.0), BigDecimal.ZERO, null);
    Food healthyYogurt = createFood("Sữa chua không đường dâu tây", 150, List.of(MealType.SNACK), List.of(), BigDecimal.valueOf(4.0), BigDecimal.valueOf(4.0), null);
    Food pho = createFood("Phở bò", 500, List.of(MealType.BREAKFAST), List.of(), BigDecimal.valueOf(2.0), BigDecimal.valueOf(2.0), null);
    Food rice = createFood("Cơm gà luộc", 600, List.of(MealType.LUNCH, MealType.DINNER), List.of(), BigDecimal.valueOf(1.0), BigDecimal.valueOf(3.0), null);

    List<Food> foods = List.of(sweetCake, healthyYogurt, pho, rice);

    RuleBasedMenuGenerator.PlanGenerationInput input = new RuleBasedMenuGenerator.PlanGenerationInput(
        1800,
        BigDecimal.valueOf(100),
        BigDecimal.valueOf(200),
        BigDecimal.valueOf(50),
        List.of(),
        List.of("DIABETES"),
        vectorEngine.defaultPreferenceVector(),
        null,
        Set.of(),
        foods,
        LocalDate.now(),
        42L
    );

    MealPlan plan = generator.generate(input);

    assertThat(plan.getMedicalWarning()).isNotNull();
    assertThat(plan.getMedicalWarning()).contains("Tiểu đường (DIABETES)");
    assertThat(plan.getItems())
        .noneMatch(item -> item.getFood().getName().equals("Bánh ngọt"));
    assertThat(plan.getTotalSugarG()).isLessThanOrEqualTo(BigDecimal.valueOf(25.0));
    assertThat(plan.getTotalCalories()).isBetween((int) Math.round(1800 * 0.95), (int) Math.round(1800 * 1.05));
  }

  @Test
  @DisplayName("Generator respects budget constraint and fits within max budget")
  void generate_respectsBudgetConstraint() {
    Food pho = createFood("Phở bò", 500, List.of(MealType.BREAKFAST), List.of(), BigDecimal.valueOf(2.0), BigDecimal.valueOf(2.0), null);
    pho.setReferencePriceVnd(40000);
    Food rice = createFood("Cơm gà luộc", 600, List.of(MealType.LUNCH, MealType.DINNER), List.of(), BigDecimal.valueOf(1.0), BigDecimal.valueOf(3.0), null);
    rice.setReferencePriceVnd(45000);
    Food cheapSalad = createFood("Salad rau củ", 150, List.of(MealType.SNACK, MealType.DINNER), List.of(), BigDecimal.valueOf(1.0), BigDecimal.valueOf(3.0), null);
    cheapSalad.setReferencePriceVnd(15000);

    List<Food> foods = List.of(pho, rice, cheapSalad);

    RuleBasedMenuGenerator.PlanGenerationInput input = new RuleBasedMenuGenerator.PlanGenerationInput(
        1500,
        BigDecimal.valueOf(80),
        BigDecimal.valueOf(150),
        BigDecimal.valueOf(40),
        List.of(),
        List.of(),
        vectorEngine.defaultPreferenceVector(),
        150000,
        Set.of(),
        foods,
        LocalDate.now(),
        42L
    );

    MealPlan plan = generator.generate(input);

    assertThat(plan.getEstimatedCostVnd()).isLessThanOrEqualTo(150000);
    assertThat(plan.getTotalCalories()).isBetween((int) Math.round(1500 * 0.95), (int) Math.round(1500 * 1.05));
  }

  @Test
  @DisplayName("Generator throws VALIDATION_ERROR when targetCalories is 0 or negative")
  void generate_targetCaloriesZeroOrNegative_throwsValidationError() {
    Food pho = createFood("Phở bò", 500, List.of(MealType.BREAKFAST), List.of(), BigDecimal.valueOf(2.0), BigDecimal.valueOf(2.0), null);
    List<Food> foods = List.of(pho);

    RuleBasedMenuGenerator.PlanGenerationInput zeroInput = new RuleBasedMenuGenerator.PlanGenerationInput(
        0,
        BigDecimal.valueOf(80),
        BigDecimal.valueOf(150),
        BigDecimal.valueOf(40),
        List.of(),
        List.of(),
        vectorEngine.defaultPreferenceVector(),
        null,
        Set.of(),
        foods,
        LocalDate.now(),
        42L
    );

    assertThatThrownBy(() -> generator.generate(zeroInput))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.VALIDATION_ERROR);

    RuleBasedMenuGenerator.PlanGenerationInput negativeInput = new RuleBasedMenuGenerator.PlanGenerationInput(
        -500,
        BigDecimal.valueOf(80),
        BigDecimal.valueOf(150),
        BigDecimal.valueOf(40),
        List.of(),
        List.of(),
        vectorEngine.defaultPreferenceVector(),
        null,
        Set.of(),
        foods,
        LocalDate.now(),
        42L
    );

    assertThatThrownBy(() -> generator.generate(negativeInput))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.VALIDATION_ERROR);
  }

  @Test
  @DisplayName("Generator ensures intra-day diversity by avoiding duplicate foods across meals")
  void generate_ensuresIntraDayDiversity() {
    Food multiMealFood = createFood("Món đa năng A", 500, List.of(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER), List.of(), BigDecimal.ONE, BigDecimal.ONE, null);
    Food lunchFood = createFood("Món trưa B", 600, List.of(MealType.LUNCH), List.of(), BigDecimal.ONE, BigDecimal.ONE, null);
    Food dinnerFood = createFood("Món tối C", 550, List.of(MealType.DINNER), List.of(), BigDecimal.ONE, BigDecimal.ONE, null);
    Food snackFood = createFood("Món xế D", 150, List.of(MealType.SNACK), List.of(), BigDecimal.ONE, BigDecimal.ONE, null);

    List<Food> foods = List.of(multiMealFood, lunchFood, dinnerFood, snackFood);

    RuleBasedMenuGenerator.PlanGenerationInput input = new RuleBasedMenuGenerator.PlanGenerationInput(
        1800,
        BigDecimal.valueOf(100),
        BigDecimal.valueOf(200),
        BigDecimal.valueOf(50),
        List.of(),
        List.of(),
        vectorEngine.defaultPreferenceVector(),
        null,
        Set.of(),
        foods,
        LocalDate.now(),
        42L
    );

    MealPlan plan = generator.generate(input);

    assertThat(plan.getItems()).hasSize(4);
    Set<UUID> chosenFoodIds = plan.getItems().stream()
        .map(item -> item.getFood().getId())
        .collect(java.util.stream.Collectors.toSet());
    assertThat(chosenFoodIds).hasSize(4);
  }

  @Test
  @DisplayName("Generator strictly excludes foods with category NON_FOOD")
  void generate_filtersOutNonFoodCategory() {
    Food nonFood = createFood("Người / Non-food object", 0, List.of(MealType.BREAKFAST, MealType.LUNCH), List.of(), BigDecimal.ZERO, BigDecimal.ZERO, null);
    nonFood.setCategory(FoodCategory.NON_FOOD);

    Food pho = createFood("Phở bò", 500, List.of(MealType.BREAKFAST), List.of(), BigDecimal.valueOf(2.0), BigDecimal.valueOf(2.0), null);
    Food rice = createFood("Cơm gà luộc", 600, List.of(MealType.LUNCH, MealType.DINNER), List.of(), BigDecimal.valueOf(1.0), BigDecimal.valueOf(3.0), null);
    Food snack = createFood("Sữa chua", 150, List.of(MealType.SNACK), List.of(), BigDecimal.valueOf(3.0), BigDecimal.ONE, null);

    List<Food> foods = List.of(nonFood, pho, rice, snack);

    RuleBasedMenuGenerator.PlanGenerationInput input = new RuleBasedMenuGenerator.PlanGenerationInput(
        1500,
        BigDecimal.valueOf(80),
        BigDecimal.valueOf(150),
        BigDecimal.valueOf(40),
        List.of(),
        List.of(),
        vectorEngine.defaultPreferenceVector(),
        null,
        Set.of(),
        foods,
        LocalDate.now(),
        42L
    );

    MealPlan plan = generator.generate(input);

    assertThat(plan.getItems())
        .noneMatch(item -> item.getFood().getCategory() == FoodCategory.NON_FOOD);
  }

  @Test
  @DisplayName("Generator filters high purine food (beef, shellfish) and sets medical warning for gout")
  void generate_goutUser_filtersHighPurineAndSetsWarning() {
    Food beefRice = createFood("Cơm thịt bò xào", 600, List.of(MealType.LUNCH, MealType.DINNER), List.of("BEEF"), BigDecimal.ONE, BigDecimal.ONE, null);
    Food chickenRice = createFood("Cơm gà luộc", 600, List.of(MealType.LUNCH, MealType.DINNER), List.of(), BigDecimal.ONE, BigDecimal.ONE, null);
    Food oatmeal = createFood("Cháo yến mạch", 400, List.of(MealType.BREAKFAST), List.of(), BigDecimal.ONE, BigDecimal.valueOf(5), null);
    Food yogurt = createFood("Sữa chua", 150, List.of(MealType.SNACK), List.of(), BigDecimal.valueOf(3), BigDecimal.ONE, null);

    List<Food> foods = List.of(beefRice, chickenRice, oatmeal, yogurt);

    RuleBasedMenuGenerator.PlanGenerationInput input = new RuleBasedMenuGenerator.PlanGenerationInput(
        1750,
        BigDecimal.valueOf(90),
        BigDecimal.valueOf(200),
        BigDecimal.valueOf(45),
        List.of(),
        List.of("GOUT"),
        vectorEngine.defaultPreferenceVector(),
        null,
        Set.of(),
        foods,
        LocalDate.now(),
        42L
    );

    MealPlan plan = generator.generate(input);

    assertThat(plan.getMedicalWarning()).contains("Bệnh Gout (GOUT)");
    assertThat(plan.getItems())
        .noneMatch(item -> item.getFood().getName().equals("Cơm thịt bò xào"));
  }

  @Test
  @DisplayName("Generator filters high protein food and sets medical warning for CKD")
  void generate_ckdUser_filtersHighProteinAndSetsWarning() {
    Food massiveProteinSteak = createFood("Bít tết siêu đạm", 600, List.of(MealType.LUNCH, MealType.DINNER), List.of(), BigDecimal.ONE, BigDecimal.ONE, null);
    massiveProteinSteak.setProteinPerServing(BigDecimal.valueOf(45.0)); // > 25g CKD limit

    Food moderateChicken = createFood("Cơm ức gà vừa", 500, List.of(MealType.LUNCH, MealType.DINNER), List.of(), BigDecimal.ONE, BigDecimal.ONE, null);
    moderateChicken.setProteinPerServing(BigDecimal.valueOf(20.0)); // <= 25g CKD limit

    Food oatmeal = createFood("Cháo yến mạch", 400, List.of(MealType.BREAKFAST), List.of(), BigDecimal.ONE, BigDecimal.valueOf(5), null);
    Food yogurt = createFood("Sữa chua", 150, List.of(MealType.SNACK), List.of(), BigDecimal.valueOf(3), BigDecimal.ONE, null);

    List<Food> foods = List.of(massiveProteinSteak, moderateChicken, oatmeal, yogurt);

    RuleBasedMenuGenerator.PlanGenerationInput input = new RuleBasedMenuGenerator.PlanGenerationInput(
        1550,
        BigDecimal.valueOf(60),
        BigDecimal.valueOf(180),
        BigDecimal.valueOf(40),
        List.of(),
        List.of("CHRONIC_KIDNEY_DISEASE"),
        vectorEngine.defaultPreferenceVector(),
        null,
        Set.of(),
        foods,
        LocalDate.now(),
        42L
    );

    MealPlan plan = generator.generate(input);

    assertThat(plan.getMedicalWarning()).contains("Bệnh thận mạn (CHRONIC KIDNEY DISEASE)");
    assertThat(plan.getItems())
        .noneMatch(item -> item.getFood().getName().equals("Bít tết siêu đạm"));
  }

  @Test
  @DisplayName("Generator throws INSUFFICIENT_FOOD_DATA when a meal type has no eligible foods and remaining foods are restricted to other meal types")
  void generate_fallbackExcludesRestrictedMealTypes() {
    Food breakfast1 = createFood("Cháo yến mạch", 400, List.of(MealType.BREAKFAST), List.of(), BigDecimal.ONE, BigDecimal.valueOf(5), null);
    Food breakfast2 = createFood("Bánh mì trứng", 450, List.of(MealType.BREAKFAST), List.of(), BigDecimal.ONE, BigDecimal.valueOf(2), null);

    List<Food> foods = List.of(breakfast1, breakfast2);

    RuleBasedMenuGenerator.PlanGenerationInput input = new RuleBasedMenuGenerator.PlanGenerationInput(
        1500,
        BigDecimal.valueOf(60),
        BigDecimal.valueOf(180),
        BigDecimal.valueOf(40),
        List.of(),
        List.of(),
        vectorEngine.defaultPreferenceVector(),
        null,
        Set.of(),
        foods,
        LocalDate.now(),
        42L
    );

    assertThatThrownBy(() -> generator.generate(input))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INSUFFICIENT_FOOD_DATA);
  }
}
