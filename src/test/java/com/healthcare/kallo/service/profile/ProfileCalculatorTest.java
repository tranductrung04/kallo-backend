package com.healthcare.kallo.service.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.model.profile.enums.ActivityLevel;
import com.healthcare.kallo.model.profile.enums.BmiCategory;
import com.healthcare.kallo.model.profile.enums.Gender;
import com.healthcare.kallo.model.profile.enums.GoalType;
import com.healthcare.kallo.service.profile.ProfileCalculator.CalculatedProfileMetrics;
import com.healthcare.kallo.service.profile.ProfileCalculator.CalorieResult;
import com.healthcare.kallo.service.profile.ProfileCalculator.MacroResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProfileCalculatorTest {

  private final LocalDate today = LocalDate.of(2026, 11, 17);

  @Nested
  @DisplayName("Age Calculation Tests")
  class AgeCalculationTests {
    @Test
    void calculateAge_validBirthDate() {
      LocalDate birthDate = LocalDate.of(1998, 5, 15);
      int age = ProfileCalculator.calculateAge(birthDate, today);
      assertThat(age).isEqualTo(28);
    }

    @Test
    void calculateAge_futureBirthDate_throwsException() {
      LocalDate futureDate = today.plusDays(1);
      assertThatThrownBy(() -> ProfileCalculator.calculateAge(futureDate, today))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.INVALID_BIRTH_DATE);
    }

    @Test
    void calculateAge_under13YearsOld_throwsException() {
      LocalDate under13 = today.minusYears(12);
      assertThatThrownBy(() -> ProfileCalculator.calculateAge(under13, today))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.INVALID_BIRTH_DATE);
    }
  }

  @Nested
  @DisplayName("BMI & Asian Classification Tests")
  class BmiTests {
    @ParameterizedTest
    @CsvSource({
      "45.0, 170.0, 15.6, UNDERWEIGHT",
      "60.0, 170.0, 20.8, NORMAL",
      "68.0, 170.0, 23.5, OVERWEIGHT",
      "80.0, 170.0, 27.7, OBESE"
    })
    void calculateBmiAndClassification(
        double weight, double height, double expectedBmi, BmiCategory expectedCategory) {
      BigDecimal bmi =
          ProfileCalculator.calculateBmi(BigDecimal.valueOf(weight), BigDecimal.valueOf(height));
      assertThat(bmi).isEqualByComparingTo(BigDecimal.valueOf(expectedBmi));

      BmiCategory category = ProfileCalculator.classifyBmi(bmi);
      assertThat(category).isEqualTo(expectedCategory);
    }
  }

  @Nested
  @DisplayName("BMR (Mifflin-St Jeor) Tests")
  class BmrTests {
    @Test
    void calculateBmr_male() {
      // Male, 70kg, 175cm, 25 yo: 10*70 + 6.25*175 - 5*25 + 5 = 700 + 1093.75 - 125 + 5 = 1673.75 -> 1674
      int bmr =
          ProfileCalculator.calculateBmr(
              Gender.MALE, BigDecimal.valueOf(70.0), BigDecimal.valueOf(175.0), 25);
      assertThat(bmr).isEqualTo(1674);
    }

    @Test
    void calculateBmr_female() {
      // Female, 55kg, 160cm, 25 yo: 10*55 + 6.25*160 - 5*25 - 161 = 550 + 1000 - 125 - 161 = 1264
      int bmr =
          ProfileCalculator.calculateBmr(
              Gender.FEMALE, BigDecimal.valueOf(55.0), BigDecimal.valueOf(160.0), 25);
      assertThat(bmr).isEqualTo(1264);
    }
  }

  @Nested
  @DisplayName("TDEE Tests")
  class TdeeTests {
    @Test
    void calculateTdee_variousActivityLevels() {
      int bmr = 1500;
      assertThat(ProfileCalculator.calculateTdee(bmr, ActivityLevel.SEDENTARY)).isEqualTo(1800);
      assertThat(ProfileCalculator.calculateTdee(bmr, ActivityLevel.LIGHTLY_ACTIVE)).isEqualTo(2063);
      assertThat(ProfileCalculator.calculateTdee(bmr, ActivityLevel.MODERATELY_ACTIVE)).isEqualTo(2325);
      assertThat(ProfileCalculator.calculateTdee(bmr, ActivityLevel.VERY_ACTIVE)).isEqualTo(2588);
      assertThat(ProfileCalculator.calculateTdee(bmr, ActivityLevel.EXTRA_ACTIVE)).isEqualTo(2850);
    }
  }

  @Nested
  @DisplayName("Calorie Goal & Safety Clamping Tests")
  class CalorieGoalTests {
    @Test
    void calculateDailyCalorieGoal_maintain() {
      CalorieResult result =
          ProfileCalculator.calculateDailyCalorieGoal(
              2200, 1500, Gender.MALE, GoalType.MAINTAIN_WEIGHT, BigDecimal.ZERO);
      assertThat(result.dailyCalorieGoal()).isEqualTo(2200);
      assertThat(result.safetyClamped()).isFalse();
    }

    @Test
    void calculateDailyCalorieGoal_loseWeight_normal() {
      // 0.5 kg/week => 550 kcal deficit
      CalorieResult result =
          ProfileCalculator.calculateDailyCalorieGoal(
              2200, 1500, Gender.MALE, GoalType.LOSE_WEIGHT, BigDecimal.valueOf(0.5));
      assertThat(result.dailyCalorieGoal()).isEqualTo(1650);
      assertThat(result.safetyClamped()).isFalse();
    }

    @Test
    void calculateDailyCalorieGoal_femaleClampedTo1200() {
      // Female TDEE 1400, deficit 550 => raw 850 < 1200 => clamped to 1200
      CalorieResult result =
          ProfileCalculator.calculateDailyCalorieGoal(
              1400, 1100, Gender.FEMALE, GoalType.LOSE_WEIGHT, BigDecimal.valueOf(0.5));
      assertThat(result.dailyCalorieGoal()).isEqualTo(1200);
      assertThat(result.safetyClamped()).isTrue();
    }

    @Test
    void calculateDailyCalorieGoal_maleClampedTo1500() {
      // Male TDEE 1800, rate 1.0 => deficit 1100 => raw 700 < 1500 => clamped to 1500
      CalorieResult result =
          ProfileCalculator.calculateDailyCalorieGoal(
              1800, 1400, Gender.MALE, GoalType.LOSE_WEIGHT, BigDecimal.valueOf(1.0));
      assertThat(result.dailyCalorieGoal()).isEqualTo(1500);
      assertThat(result.safetyClamped()).isTrue();
    }

    @Test
    void calculateDailyCalorieGoal_gainWeight() {
      // 0.5 kg/week => 550 kcal surplus
      CalorieResult result =
          ProfileCalculator.calculateDailyCalorieGoal(
              2000, 1500, Gender.MALE, GoalType.GAIN_WEIGHT, BigDecimal.valueOf(0.5));
      assertThat(result.dailyCalorieGoal()).isEqualTo(2550);
      assertThat(result.safetyClamped()).isFalse();
    }
  }

  @Nested
  @DisplayName("Macro & Water Tests")
  class MacroAndWaterTests {
    @Test
    void calculateMacros_loseWeightDefaults() {
      // LOSE_WEIGHT: 30% P, 40% C, 30% F
      MacroResult result =
          ProfileCalculator.calculateMacros(2000, GoalType.LOSE_WEIGHT, null, null, null);
      assertThat(result.customMacro()).isFalse();
      assertThat(result.proteinPct()).isEqualByComparingTo(BigDecimal.valueOf(30.0));
      assertThat(result.carbsPct()).isEqualByComparingTo(BigDecimal.valueOf(40.0));
      assertThat(result.fatPct()).isEqualByComparingTo(BigDecimal.valueOf(30.0));

      // 2000 * 0.30 / 4 = 150g Protein
      assertThat(result.proteinGoalG()).isEqualByComparingTo(BigDecimal.valueOf(150.0));
      // 2000 * 0.40 / 4 = 200g Carbs
      assertThat(result.carbsGoalG()).isEqualByComparingTo(BigDecimal.valueOf(200.0));
      // 2000 * 0.30 / 9 = 66.7g Fat
      assertThat(result.fatGoalG()).isEqualByComparingTo(BigDecimal.valueOf(66.7));
    }

    @Test
    void calculateMacros_customMacroValid() {
      MacroResult result =
          ProfileCalculator.calculateMacros(
              2000,
              GoalType.MAINTAIN_WEIGHT,
              BigDecimal.valueOf(35.0),
              BigDecimal.valueOf(45.0),
              BigDecimal.valueOf(20.0));
      assertThat(result.customMacro()).isTrue();
      assertThat(result.proteinPct()).isEqualByComparingTo(BigDecimal.valueOf(35.0));
    }

    @Test
    void calculateMacros_customMacroInvalidSum_throwsException() {
      assertThatThrownBy(
              () ->
                  ProfileCalculator.calculateMacros(
                      2000,
                      GoalType.MAINTAIN_WEIGHT,
                      BigDecimal.valueOf(40.0),
                      BigDecimal.valueOf(40.0),
                      BigDecimal.valueOf(30.0)))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.INVALID_MACRO_RATIO);
    }

    @Test
    void calculateWaterGoal_defaultAndCustom() {
      // 60kg * 35 = 2100 ml
      assertThat(ProfileCalculator.calculateWaterGoal(BigDecimal.valueOf(60.0), null))
          .isEqualTo(2100);

      // Custom
      assertThat(ProfileCalculator.calculateWaterGoal(BigDecimal.valueOf(60.0), 2500))
          .isEqualTo(2500);
    }
  }

  @Test
  @DisplayName("Full integration of calculate()")
  void fullCalculateIntegration() {
    CalculatedProfileMetrics metrics =
        ProfileCalculator.calculate(
            Gender.MALE,
            LocalDate.of(1996, 6, 20),
            today,
            BigDecimal.valueOf(175.0),
            BigDecimal.valueOf(70.0),
            ActivityLevel.MODERATELY_ACTIVE,
            GoalType.LOSE_WEIGHT,
            BigDecimal.valueOf(0.5),
            null,
            null,
            null,
            null);

    assertThat(metrics.age()).isEqualTo(30);
    assertThat(metrics.bmi()).isEqualByComparingTo(BigDecimal.valueOf(22.9));
    assertThat(metrics.bmiCategory()).isEqualTo(BmiCategory.NORMAL);
    assertThat(metrics.bmr()).isEqualTo(1649);
    assertThat(metrics.tdee()).isEqualTo(2556);
    assertThat(metrics.dailyCalorieGoal()).isEqualTo(2006); // 2556 - 550
    assertThat(metrics.safetyClamped()).isFalse();
    assertThat(metrics.waterGoalMl()).isEqualTo(2450); // 70 * 35
  }
}
