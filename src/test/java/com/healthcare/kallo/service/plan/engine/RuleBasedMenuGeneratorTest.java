package com.healthcare.kallo.service.plan.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.model.food.Food;
import com.healthcare.kallo.model.food.enums.FoodCategory;
import com.healthcare.kallo.model.food.enums.FoodSource;
import com.healthcare.kallo.model.food.enums.MealType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class RuleBasedMenuGeneratorTest {

  private VectorSimilarityEngine vectorEngine;
  private RuleBasedMenuGenerator generator;

  @BeforeEach
  void setUp() {
    vectorEngine = new VectorSimilarityEngine();
    generator = new RuleBasedMenuGenerator(vectorEngine);
  }

  @Test
  void testGeneratePlan_DiabetesConflictWithSweetPreference() {
    Food sweetPho =
        makeFood(
            "Chè ngọt sáng",
            FoodCategory.SOUP,
            450,
            10,
            18.0,
            40000,
            List.of("BREAKFAST", "LUNCH", "DINNER", "SNACK"),
            List.of(1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.5, 0.0, 0.8, 0.0, 1.0, 0.1));
    List<Food> pool =
        List.of(
            sweetPho,
            makeFood(
                "Phở bò",
                FoodCategory.NOODLE,
                450,
                29,
                3.0,
                50000,
                List.of("BREAKFAST", "LUNCH", "DINNER"),
                List.of(1.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.1, 0.2, 0.8, 0.1, 0.1, 0.7)),
            makeFood(
                "Cơm tấm gà",
                FoodCategory.RICE,
                630,
                35,
                4.0,
                50000,
                List.of("LUNCH", "DINNER"),
                List.of(0.0, 1.0, 0.0, 0.0, 1.0, 0.0, 0.1, 0.5, 0.5, 0.1, 0.2, 0.7)),
            makeFood(
                "Bún chả",
                FoodCategory.NOODLE,
                540,
                31,
                5.0,
                45000,
                List.of("LUNCH", "DINNER"),
                List.of(0.3, 0.8, 0.0, 1.0, 0.0, 0.0, 0.1, 0.6, 0.3, 0.1, 0.2, 0.6)),
            makeFood(
                "Súp cua",
                FoodCategory.SOUP,
                190,
                13,
                2.0,
                25000,
                List.of("BREAKFAST", "SNACK", "DINNER"),
                List.of(1.0, 0.0, 0.0, 0.0, 0.2, 0.8, 0.1, 0.1, 0.9, 0.0, 0.1, 0.7)));

    double[] sweetPref = new double[] {0.5, 0.5, 0.2, 0.2, 0.2, 0.2, 0.5, 0.2, 0.5, 0.0, 1.0, 0.4};
    RuleBasedMenuGenerator.GeneratedPlan plan =
        generator.generate(
            new RuleBasedMenuGenerator.GenerationInput(
                LocalDate.of(2026, 11, 25),
                1,
                1800,
                100.0,
                null,
                sweetPref,
                Set.of("DIABETES_T2"),
                pool,
                Set.of(),
                new Random(42)));

    assertThat(plan.days()).hasSize(1);
    assertThat(plan.totalSugarG()).isLessThanOrEqualTo(new BigDecimal("25.0"));
    assertThat(plan.days().get(0).items())
        .allSatisfy(item -> assertThat(item.food().getSugarPerServing()).isLessThanOrEqualTo(new BigDecimal("10.0")));
    assertThat(plan.medicalWarning()).contains("DIABETES");
  }

  @Test
  void testGeneratePlan_GoutAndHypertensionMasks() {
    List<Food> pool = standardDiversePool();
    double[] neutralPref = vectorEngine.normalizePreference(null);

    RuleBasedMenuGenerator.GeneratedPlan plan =
        generator.generate(
            new RuleBasedMenuGenerator.GenerationInput(
                LocalDate.of(2026, 11, 25),
                1,
                1900,
                110.0,
                null,
                neutralPref,
                Set.of("GOUT", "HYPERTENSION"),
                pool,
                Set.of(),
                new Random(7)));

    assertThat(plan.medicalWarning()).contains("Purine").contains("Sodium");
    assertThat(plan.days().get(0).totalCalories()).isBetween(1805, 1995);
  }

  @Test
  void testGeneratePlan_CosineSimilarityPreferenceMatch() {
    Food soupBreakfast =
        makeFood(
            "Phở bò",
            FoodCategory.NOODLE,
            450,
            29,
            2.0,
            45000,
            List.of("BREAKFAST", "LUNCH", "DINNER"),
            List.of(1.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.1, 0.9, 0.0, 0.0, 0.8));
    Food dryBreakfast =
        makeFood(
            "Xôi chiên",
            FoodCategory.STAPLE,
            450,
            15,
            2.0,
            25000,
            List.of("BREAKFAST", "LUNCH", "DINNER"),
            List.of(0.0, 1.0, 0.0, 0.9, 0.0, 0.0, 0.0, 0.9, 0.0, 0.0, 0.0, 0.2));
    Food snack =
        makeFood(
            "Sữa chua hạt",
            FoodCategory.SNACK,
            180,
            10,
            4.0,
            20000,
            List.of("SNACK"),
            List.of(0.0, 0.2, 0.0, 0.0, 0.0, 0.0, 0.8, 0.0, 1.0, 0.0, 0.2, 0.5));

    double[] soupLover = new double[] {1.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.8};
    RuleBasedMenuGenerator.GeneratedPlan plan =
        generator.generate(
            new RuleBasedMenuGenerator.GenerationInput(
                LocalDate.of(2026, 11, 25),
                1,
                1600,
                90.0,
                null,
                soupLover,
                Set.of(),
                List.of(soupBreakfast, dryBreakfast, snack),
                Set.of(),
                new Random(1)));

    long soupCount =
        plan.days().get(0).items().stream()
            .filter(i -> i.food().getId().equals(soupBreakfast.getId()))
            .count();
    assertThat(soupCount).isGreaterThanOrEqualTo(1);
    assertThat(plan.preferenceMatchScore()).isGreaterThan(BigDecimal.ZERO);
  }

  @Test
  void testGeneratePlan_ZeroFeatureVectorCustomRecipe() {
    Food zeroCustom =
        makeFood(
            "Món Custom Cơm Gà",
            FoodCategory.RICE,
            500,
            32,
            2.0,
            40000,
            List.of("BREAKFAST", "LUNCH", "DINNER"),
            List.of(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0));
    zeroCustom.setSource(FoodSource.USER_CUSTOM);
    Food zeroSnack =
        makeFood(
            "Sinh tố custom",
            FoodCategory.SNACK,
            180,
            6,
            4.0,
            25000,
            List.of("SNACK"),
            List.of(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0));
    zeroSnack.setSource(FoodSource.USER_CUSTOM);

    RuleBasedMenuGenerator.GeneratedPlan plan =
        generator.generate(
            new RuleBasedMenuGenerator.GenerationInput(
                LocalDate.of(2026, 11, 25),
                1,
                1700,
                100.0,
                null,
                vectorEngine.normalizePreference(null),
                Set.of(),
                List.of(zeroCustom, zeroSnack),
                Set.of(),
                new Random(10)));

    assertThat(plan.preferenceMatchScore()).isEqualByComparingTo("0.50");
  }

  @Test
  void testGeneratePlan_BudgetFilterExcludesNullPrice() {
    Food nullPrice =
        makeFood(
            "Món không giá",
            FoodCategory.RICE,
            600,
            30,
            2.0,
            null,
            List.of("BREAKFAST", "LUNCH", "DINNER"),
            List.of(0.0, 1.0, 0.0, 0.5, 0.0, 0.0, 0.1, 0.2, 0.8, 0.0, 0.0, 0.5));
    List<Food> pool = new java.util.ArrayList<>(standardDiversePool());
    pool.add(nullPrice);

    RuleBasedMenuGenerator.GeneratedPlan plan =
        generator.generate(
            new RuleBasedMenuGenerator.GenerationInput(
                LocalDate.of(2026, 11, 25),
                1,
                1800,
                100.0,
                140000,
                vectorEngine.normalizePreference(null),
                Set.of(),
                pool,
                Set.of(),
                new Random(5)));

    assertThat(plan.estimatedCostVnd()).isLessThanOrEqualTo(140000 + 100000);
    assertThat(plan.days().get(0).items())
        .noneMatch(i -> i.food().getId().equals(nullPrice.getId()));
  }

  @Test
  void testGeneratePlan_SnackFallbackWhenNoSnackTag() {
    List<Food> noSnackTaggedPool =
        List.of(
            makeFood(
                "Bún cá",
                FoodCategory.NOODLE,
                420,
                25,
                2.0,
                40000,
                List.of("BREAKFAST", "LUNCH", "DINNER"),
                List.of(1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.2, 0.2, 0.8, 0.1, 0.1, 0.7)),
            makeFood(
                "Cơm tấm",
                FoodCategory.RICE,
                680,
                32,
                3.0,
                50000,
                List.of("BREAKFAST", "LUNCH", "DINNER"),
                List.of(0.0, 1.0, 0.0, 1.0, 0.0, 0.0, 0.1, 0.8, 0.1, 0.1, 0.2, 0.6)),
            makeFood(
                "Trứng luộc",
                FoodCategory.STAPLE,
                150,
                12,
                1.0,
                10000,
                List.of("BREAKFAST", "LUNCH", "DINNER"),
                List.of(0.0, 0.8, 0.0, 0.0, 0.3, 0.0, 0.2, 0.1, 0.9, 0.0, 0.0, 0.8)));

    RuleBasedMenuGenerator.GeneratedPlan plan =
        generator.generate(
            new RuleBasedMenuGenerator.GenerationInput(
                LocalDate.of(2026, 11, 25),
                1,
                1750,
                100.0,
                null,
                vectorEngine.normalizePreference(null),
                Set.of(),
                noSnackTaggedPool,
                Set.of(),
                new Random(9)));

    assertThat(plan.days().get(0).totalCalories()).isBetween(1662, 1838);
  }

  @Test
  void testGeneratePlan_HardConstraintFailsWithInsufficientFoodData() {
    Food sugaryOnly =
        makeFood(
            "Bánh kem",
            FoodCategory.SNACK,
            500,
            6,
            25.0,
            80000,
            List.of("BREAKFAST", "LUNCH", "DINNER", "SNACK"),
            List.of(0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.5, 0.7, 0.0, 0.0, 1.0, 0.1));

    assertThatThrownBy(
            () ->
                generator.generate(
                    new RuleBasedMenuGenerator.GenerationInput(
                        LocalDate.of(2026, 11, 25),
                        1,
                        1800,
                        100.0,
                        null,
                        vectorEngine.normalizePreference(null),
                        Set.of("DIABETES_T2"),
                        List.of(sugaryOnly),
                        Set.of(),
                        new Random(1))))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INSUFFICIENT_FOOD_DATA);
  }

  @Test
  void testGeneratePlan_Weekly7DaysPerformanceAndVariety() {
    List<Food> pool = standardDiversePool();

    long startNs = System.nanoTime();
    RuleBasedMenuGenerator.GeneratedPlan plan =
        generator.generate(
            new RuleBasedMenuGenerator.GenerationInput(
                LocalDate.of(2026, 11, 25),
                7,
                2000,
                120.0,
                null,
                vectorEngine.normalizePreference(null),
                Set.of(),
                pool,
                Set.of(),
                new Random(123)));
    long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;

    assertThat(elapsedMs).isLessThan(200L);
    assertThat(plan.days()).hasSize(7);
    for (int i = 0; i < plan.days().size(); i++) {
      RuleBasedMenuGenerator.GeneratedDay day = plan.days().get(i);
      assertThat(day.dayIndex()).isEqualTo(i + 1);
      assertThat(day.totalCalories()).isBetween(1900, 2100);
    }

    long distinctFoods =
        plan.days().stream()
            .flatMap(d -> d.items().stream())
            .map(i -> i.food().getId())
            .distinct()
            .count();
    assertThat(distinctFoods).isGreaterThanOrEqualTo(6L);
  }

  @Test
  void testGeneratePlan_BreakfastOneDishLunchDinnerThreeToFourDishesWithFlexibleBudget() {
    List<Food> pool = vietFoodLikePool();

    RuleBasedMenuGenerator.GeneratedPlan plan =
        generator.generate(
            new RuleBasedMenuGenerator.GenerationInput(
                LocalDate.of(2026, 11, 26),
                1,
                1928,
                100.0,
                100000,
                vectorEngine.normalizePreference(null),
                Set.of(),
                pool,
                Set.of(),
                new Random(42)));

    RuleBasedMenuGenerator.GeneratedDay day = plan.days().get(0);
    assertThat(day.totalCalories()).isBetween(1832, 2024);
    assertThat(day.estimatedCostVnd()).isLessThanOrEqualTo(200000);

    List<RuleBasedMenuGenerator.GeneratedItem> breakfast =
        day.items().stream().filter(i -> i.mealType() == MealType.BREAKFAST).toList();
    List<RuleBasedMenuGenerator.GeneratedItem> lunch =
        day.items().stream().filter(i -> i.mealType() == MealType.LUNCH).toList();
    List<RuleBasedMenuGenerator.GeneratedItem> dinner =
        day.items().stream().filter(i -> i.mealType() == MealType.DINNER).toList();
    List<RuleBasedMenuGenerator.GeneratedItem> snack =
        day.items().stream().filter(i -> i.mealType() == MealType.SNACK).toList();

    assertThat(breakfast).hasSize(1);
    assertThat(breakfast.get(0).food().getName()).isNotIn("Cơm", "Bún", "Đậu hũ", "Chanh");
    assertThat(lunch).hasSizeBetween(3, 4);
    assertThat(dinner).hasSizeBetween(3, 4);
    assertThat(snack).hasSize(1);
    assertThat(snack.get(0).food().getCaloriesPerServing()).isLessThanOrEqualTo(320);
    assertThat(day.items()).noneMatch(i -> i.food().getName().equals("Chanh"));
  }

  @Test
  void testGeneratePlan_WeeklyVietFoodPoolRotatesMealsAndKeepsStructure() {
    List<Food> pool = vietFoodLikePool();

    RuleBasedMenuGenerator.GeneratedPlan plan =
        generator.generate(
            new RuleBasedMenuGenerator.GenerationInput(
                LocalDate.of(2026, 11, 26),
                7,
                1928,
                100.0,
                100000,
                vectorEngine.normalizePreference(null),
                Set.of(),
                pool,
                Set.of(),
                new Random(99)));

    assertThat(plan.days()).hasSize(7);
    for (RuleBasedMenuGenerator.GeneratedDay day : plan.days()) {
      assertThat(day.estimatedCostVnd()).isLessThanOrEqualTo(200000);
      assertThat(day.items().stream().filter(i -> i.mealType() == MealType.BREAKFAST).count())
          .isEqualTo(1L);
      assertThat(day.items().stream().filter(i -> i.mealType() == MealType.LUNCH).count())
          .isBetween(3L, 4L);
      assertThat(day.items().stream().filter(i -> i.mealType() == MealType.DINNER).count())
          .isBetween(3L, 4L);
    }

    long distinctBreakfasts =
        plan.days().stream()
            .flatMap(d -> d.items().stream())
            .filter(i -> i.mealType() == MealType.BREAKFAST)
            .map(i -> i.food().getName())
            .distinct()
            .count();
    assertThat(distinctBreakfasts).isGreaterThanOrEqualTo(4L);
  }

  private List<Food> vietFoodLikePool() {
    return List.of(
        makeFood(
            "Phở",
            FoodCategory.NOODLE,
            450,
            29,
            4.0,
            50000,
            List.of("BREAKFAST", "LUNCH", "DINNER"),
            List.of(1.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.1, 0.2, 0.8, 0.2, 0.1, 0.7)),
        makeFood(
            "Bún bò Huế",
            FoodCategory.NOODLE,
            475,
            31,
            4.0,
            50000,
            List.of("BREAKFAST", "LUNCH", "DINNER"),
            List.of(1.0, 0.0, 0.8, 0.4, 0.0, 0.0, 0.1, 0.3, 0.7, 0.8, 0.1, 0.8)),
        makeFood(
            "Bún riêu",
            FoodCategory.NOODLE,
            442,
            28,
            4.3,
            45000,
            List.of("BREAKFAST", "LUNCH", "DINNER"),
            List.of(1.0, 0.0, 0.0, 0.4, 0.0, 0.9, 0.2, 0.2, 0.8, 0.2, 0.1, 0.7)),
        makeFood(
            "Hủ tiếu",
            FoodCategory.NOODLE,
            428,
            25,
            3.6,
            45000,
            List.of("BREAKFAST", "LUNCH", "DINNER"),
            List.of(1.0, 0.0, 0.0, 0.7, 0.0, 0.5, 0.1, 0.1, 0.9, 0.1, 0.2, 0.6)),
        makeFood(
            "Bánh cuốn",
            FoodCategory.STAPLE,
            363,
            13,
            2.0,
            35000,
            List.of("BREAKFAST", "DINNER"),
            List.of(0.0, 1.0, 0.0, 0.7, 0.0, 0.0, 0.2, 0.2, 0.9, 0.1, 0.1, 0.4)),
        makeFood(
            "Bánh mì",
            FoodCategory.STAPLE,
            450,
            18,
            4.5,
            25000,
            List.of("BREAKFAST", "LUNCH", "DINNER", "SNACK"),
            List.of(0.0, 1.0, 0.0, 0.8, 0.2, 0.0, 0.1, 0.4, 0.2, 0.2, 0.2, 0.4)),
        makeFood(
            "Xôi",
            FoodCategory.STAPLE,
            520,
            17,
            3.0,
            25000,
            List.of("BREAKFAST", "LUNCH", "DINNER"),
            List.of(0.0, 1.0, 0.0, 0.6, 0.4, 0.0, 0.1, 0.4, 0.7, 0.1, 0.2, 0.3)),
        makeFood(
            "Cơm",
            FoodCategory.RICE,
            195,
            4,
            0.2,
            5000,
            List.of("LUNCH", "DINNER"),
            List.of(0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.9, 0.0, 1.0, 0.0, 0.1, 0.1)),
        makeFood(
            "Bún",
            FoodCategory.STAPLE,
            165,
            3,
            0.2,
            10000,
            List.of("BREAKFAST", "LUNCH", "DINNER"),
            List.of(0.2, 0.8, 0.0, 0.0, 0.0, 0.0, 0.9, 0.0, 0.9, 0.0, 0.0, 0.1)),
        makeFood(
            "Đậu hũ",
            FoodCategory.STAPLE,
            210,
            17,
            0.8,
            10000,
            List.of("LUNCH", "DINNER"),
            List.of(0.0, 0.9, 0.0, 0.0, 0.0, 0.0, 1.0, 0.7, 0.3, 0.0, 0.0, 0.8)),
        makeFood(
            "Cá",
            FoodCategory.SEAFOOD,
            270,
            31,
            0.0,
            35000,
            List.of("LUNCH", "DINNER"),
            List.of(0.0, 0.8, 0.0, 0.0, 0.0, 1.0, 0.0, 0.9, 0.1, 0.1, 0.0, 1.0)),
        makeFood(
            "Thịt gà",
            FoodCategory.MEAT,
            344,
            39,
            0.0,
            40000,
            List.of("LUNCH", "DINNER"),
            List.of(0.0, 0.9, 0.0, 0.0, 1.0, 0.0, 0.0, 0.4, 0.7, 0.1, 0.0, 1.0)),
        makeFood(
            "Thịt bò",
            FoodCategory.MEAT,
            315,
            39,
            0.0,
            65000,
            List.of("LUNCH", "DINNER"),
            List.of(0.0, 0.9, 1.0, 0.0, 0.0, 0.0, 0.0, 0.6, 0.3, 0.2, 0.0, 1.0)),
        makeFood(
            "Tôm",
            FoodCategory.SEAFOOD,
            149,
            36,
            0.0,
            60000,
            List.of("LUNCH", "DINNER"),
            List.of(0.0, 0.6, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 1.0, 0.0, 0.0, 1.0)),
        makeFood(
            "Rau",
            FoodCategory.VEGETABLE,
            60,
            4,
            0.8,
            20000,
            List.of("LUNCH", "DINNER"),
            List.of(0.0, 0.3, 0.0, 0.0, 0.0, 0.0, 1.0, 0.5, 0.5, 0.1, 0.1, 0.4)),
        makeFood(
            "Bông cải",
            FoodCategory.VEGETABLE,
            51,
            4,
            2.6,
            20000,
            List.of("LUNCH", "DINNER"),
            List.of(0.0, 0.2, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 1.0, 0.0, 0.1, 0.4)),
        makeFood(
            "Salad",
            FoodCategory.VEGETABLE,
            99,
            3,
            4.5,
            35000,
            List.of("BREAKFAST", "LUNCH", "DINNER"),
            List.of(0.0, 0.2, 0.0, 0.0, 0.0, 0.0, 0.9, 0.1, 0.9, 0.0, 0.2, 0.2)),
        makeFood(
            "Chanh",
            FoodCategory.VEGETABLE,
            17,
            1,
            1.5,
            3000,
            List.of("BREAKFAST", "LUNCH", "DINNER", "SNACK"),
            List.of(0.0, 0.1, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.8, 0.0, 0.1, 0.1)),
        makeFood(
            "Canh",
            FoodCategory.SOUP,
            70,
            4,
            1.5,
            15000,
            List.of("LUNCH", "DINNER"),
            List.of(1.0, 0.0, 0.0, 0.2, 0.0, 0.0, 0.9, 0.0, 1.0, 0.0, 0.1, 0.2)),
        makeFood(
            "Khổ qua thịt",
            FoodCategory.SOUP,
            150,
            12,
            2.0,
            30000,
            List.of("LUNCH", "DINNER"),
            List.of(1.0, 0.0, 0.0, 0.8, 0.0, 0.0, 0.4, 0.1, 0.9, 0.0, 0.1, 0.8)),
        makeFood(
            "Bánh tráng",
            FoodCategory.SNACK,
            165,
            2,
            0.3,
            10000,
            List.of("SNACK"),
            List.of(0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.7, 0.1, 0.1, 0.1, 0.1, 0.1)),
        makeFood(
            "Súp cua",
            FoodCategory.SOUP,
            205,
            13,
            2.0,
            30000,
            List.of("BREAKFAST", "SNACK", "DINNER"),
            List.of(1.0, 0.0, 0.0, 0.0, 0.3, 0.9, 0.1, 0.1, 0.9, 0.1, 0.1, 0.7)));
  }

  private List<Food> standardDiversePool() {
    return List.of(
        makeFood(
            "Phở bò",
            FoodCategory.NOODLE,
            450,
            29,
            3.0,
            45000,
            List.of("BREAKFAST", "LUNCH", "DINNER"),
            List.of(1.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.1, 0.2, 0.8, 0.2, 0.1, 0.7)),
        makeFood(
            "Bánh cuốn",
            FoodCategory.STAPLE,
            365,
            13,
            2.0,
            30000,
            List.of("BREAKFAST", "DINNER"),
            List.of(0.0, 1.0, 0.0, 0.7, 0.0, 0.0, 0.2, 0.2, 0.9, 0.1, 0.1, 0.4)),
        makeFood(
            "Bún riêu",
            FoodCategory.NOODLE,
            440,
            28,
            3.5,
            35000,
            List.of("BREAKFAST", "LUNCH", "DINNER"),
            List.of(1.0, 0.0, 0.0, 0.4, 0.0, 0.9, 0.2, 0.2, 0.8, 0.2, 0.1, 0.7)),
        makeFood(
            "Hủ tiếu",
            FoodCategory.NOODLE,
            430,
            25,
            3.0,
            35000,
            List.of("BREAKFAST", "LUNCH", "DINNER"),
            List.of(1.0, 0.0, 0.0, 0.7, 0.0, 0.5, 0.1, 0.1, 0.9, 0.1, 0.2, 0.6)),
        makeFood(
            "Cơm tấm",
            FoodCategory.RICE,
            680,
            33,
            4.0,
            45000,
            List.of("BREAKFAST", "LUNCH", "DINNER"),
            List.of(0.0, 1.0, 0.0, 1.0, 0.0, 0.0, 0.1, 0.8, 0.1, 0.1, 0.3, 0.5)),
        makeFood(
            "Mì Quảng",
            FoodCategory.NOODLE,
            540,
            32,
            4.0,
            40000,
            List.of("BREAKFAST", "LUNCH", "DINNER"),
            List.of(0.4, 0.7, 0.0, 0.6, 0.2, 0.6, 0.1, 0.3, 0.7, 0.3, 0.2, 0.6)),
        makeFood(
            "Cơm",
            FoodCategory.RICE,
            195,
            4,
            0.2,
            5000,
            List.of("LUNCH", "DINNER"),
            List.of(0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.9, 0.0, 1.0, 0.0, 0.1, 0.1)),
        makeFood(
            "Thịt gà luộc",
            FoodCategory.MEAT,
            340,
            39,
            0.0,
            35000,
            List.of("LUNCH", "DINNER"),
            List.of(0.0, 0.9, 0.0, 0.0, 1.0, 0.0, 0.0, 0.2, 0.9, 0.0, 0.0, 1.0)),
        makeFood(
            "Bông cải luộc",
            FoodCategory.VEGETABLE,
            51,
            4,
            2.0,
            15000,
            List.of("LUNCH", "DINNER"),
            List.of(0.0, 0.2, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 1.0, 0.0, 0.1, 0.4)),
        makeFood(
            "Gỏi cuốn",
            FoodCategory.SNACK,
            200,
            12,
            2.5,
            25000,
            List.of("LUNCH", "DINNER", "SNACK"),
            List.of(0.0, 0.8, 0.0, 0.5, 0.0, 0.6, 0.5, 0.0, 1.0, 0.1, 0.2, 0.5)),
        makeFood(
            "Súp cua",
            FoodCategory.SOUP,
            205,
            13,
            2.0,
            25000,
            List.of("BREAKFAST", "SNACK", "DINNER"),
            List.of(1.0, 0.0, 0.0, 0.0, 0.3, 0.9, 0.1, 0.1, 0.9, 0.1, 0.1, 0.7)));
  }

  private Food makeFood(
      String name,
      FoodCategory category,
      int cal,
      int pro,
      double sugar,
      Integer priceVnd,
      List<String> meals,
      List<Double> vector) {
    Food food = new Food();
    ReflectionTestUtils.setField(food, "id", UUID.randomUUID());
    food.setName(name);
    food.setCategory(category);
    food.setDefaultServingName("1 phần");
    food.setDefaultServingGrams(new BigDecimal("250.0"));
    food.setCaloriesPerServing(cal);
    food.setProteinPerServing(BigDecimal.valueOf(pro));
    food.setCarbsPerServing(new BigDecimal("40.0"));
    food.setFatPerServing(new BigDecimal("8.0"));
    food.setSugarPerServing(BigDecimal.valueOf(sugar));
    food.setFiberPerServing(new BigDecimal("3.0"));
    food.setReferencePriceVnd(priceVnd);
    food.setSuitableMealTypes(meals);
    food.setFeatureVector(vector);
    food.setSource(FoodSource.SYSTEM);
    return food;
  }
}
