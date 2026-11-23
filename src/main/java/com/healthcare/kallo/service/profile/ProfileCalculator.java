package com.healthcare.kallo.service.profile;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.model.profile.enums.ActivityLevel;
import com.healthcare.kallo.model.profile.enums.BmiCategory;
import com.healthcare.kallo.model.profile.enums.Gender;
import com.healthcare.kallo.model.profile.enums.GoalType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;

/**
 * Pure Domain Service calculating biometrics and nutrition targets (Appendix A of SRS).
 * Does not depend on Spring Context or Database for 100% automated testability.
 */
public final class ProfileCalculator {

  private ProfileCalculator() {}

  public record CalculatedProfileMetrics(
      int age,
      BigDecimal bmi,
      BmiCategory bmiCategory,
      int bmr,
      int tdee,
      int dailyCalorieGoal,
      boolean safetyClamped,
      BigDecimal proteinGoalG,
      BigDecimal carbsGoalG,
      BigDecimal fatGoalG,
      BigDecimal proteinPct,
      BigDecimal carbsPct,
      BigDecimal fatPct,
      boolean customMacro,
      int waterGoalMl) {}

  public static CalculatedProfileMetrics calculate(
      Gender gender,
      LocalDate birthDate,
      LocalDate today,
      BigDecimal heightCm,
      BigDecimal weightKg,
      ActivityLevel activityLevel,
      GoalType goalType,
      BigDecimal weeklyRateKg,
      BigDecimal customProteinPct,
      BigDecimal customCarbsPct,
      BigDecimal customFatPct,
      Integer customWaterGoalMl) {

    int age = calculateAge(birthDate, today);
    BigDecimal bmi = calculateBmi(weightKg, heightCm);
    BmiCategory bmiCategory = classifyBmi(bmi);
    int bmr = calculateBmr(gender, weightKg, heightCm, age);
    int tdee = calculateTdee(bmr, activityLevel);

    CalorieResult calorieResult =
        calculateDailyCalorieGoal(tdee, bmr, gender, goalType, weeklyRateKg);

    MacroResult macroResult =
        calculateMacros(
            calorieResult.dailyCalorieGoal(),
            goalType,
            customProteinPct,
            customCarbsPct,
            customFatPct);

    int waterGoalMl = calculateWaterGoal(weightKg, customWaterGoalMl);

    return new CalculatedProfileMetrics(
        age,
        bmi,
        bmiCategory,
        bmr,
        tdee,
        calorieResult.dailyCalorieGoal(),
        calorieResult.safetyClamped(),
        macroResult.proteinGoalG(),
        macroResult.carbsGoalG(),
        macroResult.fatGoalG(),
        macroResult.proteinPct(),
        macroResult.carbsPct(),
        macroResult.fatPct(),
        macroResult.customMacro(),
        waterGoalMl);
  }

  public static int calculateAge(LocalDate birthDate, LocalDate today) {
    if (birthDate == null || birthDate.isAfter(today)) {
      throw new BusinessException(ErrorCode.INVALID_BIRTH_DATE);
    }
    int age = Period.between(birthDate, today).getYears();
    if (age < 13 || age > 120) {
      throw new BusinessException(ErrorCode.INVALID_BIRTH_DATE);
    }
    return age;
  }

  public static BigDecimal calculateBmi(BigDecimal weightKg, BigDecimal heightCm) {
    double heightM = heightCm.doubleValue() / 100.0;
    double rawBmi = weightKg.doubleValue() / (heightM * heightM);
    return BigDecimal.valueOf(rawBmi).setScale(1, RoundingMode.HALF_UP);
  }

  public static BmiCategory classifyBmi(BigDecimal bmi) {
    double val = bmi.doubleValue();
    if (val < 18.5) {
      return BmiCategory.UNDERWEIGHT;
    } else if (val <= 22.9) {
      return BmiCategory.NORMAL;
    } else if (val <= 24.9) {
      return BmiCategory.OVERWEIGHT;
    } else {
      return BmiCategory.OBESE;
    }
  }

  public static int calculateBmr(Gender gender, BigDecimal weightKg, BigDecimal heightCm, int age) {
    double w = weightKg.doubleValue();
    double h = heightCm.doubleValue();
    double rawBmr;
    if (gender == Gender.MALE) {
      rawBmr = 10.0 * w + 6.25 * h - 5.0 * age + 5.0;
    } else if (gender == Gender.FEMALE) {
      rawBmr = 10.0 * w + 6.25 * h - 5.0 * age - 161.0;
    } else {
      // For OTHER: average between male and female formula (+5 - 161) / 2 = -78
      rawBmr = 10.0 * w + 6.25 * h - 5.0 * age - 78.0;
    }
    return (int) Math.round(rawBmr);
  }

  public static int calculateTdee(int bmr, ActivityLevel activityLevel) {
    return (int) Math.round(bmr * activityLevel.getMultiplier());
  }

  public record CalorieResult(int dailyCalorieGoal, boolean safetyClamped) {}

  public static CalorieResult calculateDailyCalorieGoal(
      int tdee, int bmr, Gender gender, GoalType goalType, BigDecimal weeklyRateKg) {
    int rawCalories;
    if (goalType == GoalType.MAINTAIN_WEIGHT) {
      rawCalories = tdee;
    } else {
      double rate =
          (weeklyRateKg != null && weeklyRateKg.doubleValue() > 0)
              ? weeklyRateKg.doubleValue()
              : 0.5;
      // 7700 kcal ≈ 1 kg fat => daily deficit/surplus = rate * 7700 / 7 = rate * 1100
      int dailyAdjustment = (int) Math.round(rate * 1100.0);
      if (goalType == GoalType.LOSE_WEIGHT) {
        rawCalories = tdee - dailyAdjustment;
      } else {
        rawCalories = tdee + dailyAdjustment;
      }
    }

    // Safety Clamping: minimum 1200 kcal (female), 1500 kcal (male), 1350 kcal (other)
    int minSafeThreshold;
    if (gender == Gender.FEMALE) {
      minSafeThreshold = 1200;
    } else if (gender == Gender.MALE) {
      minSafeThreshold = 1500;
    } else {
      minSafeThreshold = 1350;
    }

    if (rawCalories < minSafeThreshold) {
      return new CalorieResult(minSafeThreshold, true);
    }
    return new CalorieResult(rawCalories, false);
  }

  public record MacroResult(
      BigDecimal proteinGoalG,
      BigDecimal carbsGoalG,
      BigDecimal fatGoalG,
      BigDecimal proteinPct,
      BigDecimal carbsPct,
      BigDecimal fatPct,
      boolean customMacro) {}

  public static MacroResult calculateMacros(
      int dailyCalorieGoal,
      GoalType goalType,
      BigDecimal customProteinPct,
      BigDecimal customCarbsPct,
      BigDecimal customFatPct) {

    boolean anyCustom =
        customProteinPct != null || customCarbsPct != null || customFatPct != null;
    boolean hasCustom =
        customProteinPct != null && customCarbsPct != null && customFatPct != null;

    if (anyCustom && !hasCustom) {
      throw new BusinessException(
          ErrorCode.INVALID_MACRO_RATIO,
          "Khi tùy biến tỷ lệ dinh dưỡng (macro), bạn phải nhập đủ cả 3 chỉ số: đạm (protein), bột đường (carbs) và chất béo (fat)");
    }

    BigDecimal pPct;
    BigDecimal cPct;
    BigDecimal fPct;
    boolean isCustom;

    if (hasCustom) {
      double total =
          customProteinPct.doubleValue() + customCarbsPct.doubleValue() + customFatPct.doubleValue();
      if (Math.abs(total - 100.0) > 0.5) {
        throw new BusinessException(ErrorCode.INVALID_MACRO_RATIO);
      }
      pPct = customProteinPct.setScale(1, RoundingMode.HALF_UP);
      cPct = customCarbsPct.setScale(1, RoundingMode.HALF_UP);
      fPct = customFatPct.setScale(1, RoundingMode.HALF_UP);
      isCustom = true;
    } else {
      isCustom = false;
      if (goalType == GoalType.LOSE_WEIGHT) {
        pPct = BigDecimal.valueOf(30.0);
        cPct = BigDecimal.valueOf(40.0);
        fPct = BigDecimal.valueOf(30.0);
      } else if (goalType == GoalType.GAIN_WEIGHT) {
        pPct = BigDecimal.valueOf(25.0);
        cPct = BigDecimal.valueOf(50.0);
        fPct = BigDecimal.valueOf(25.0);
      } else {
        // MAINTAIN_WEIGHT
        pPct = BigDecimal.valueOf(20.0);
        cPct = BigDecimal.valueOf(50.0);
        fPct = BigDecimal.valueOf(30.0);
      }
    }

    // 1g Protein = 4 kcal, 1g Carbs = 4 kcal, 1g Fat = 9 kcal
    double cal = dailyCalorieGoal;
    double pGrams = (cal * (pPct.doubleValue() / 100.0)) / 4.0;
    double cGrams = (cal * (cPct.doubleValue() / 100.0)) / 4.0;
    double fGrams = (cal * (fPct.doubleValue() / 100.0)) / 9.0;

    return new MacroResult(
        BigDecimal.valueOf(pGrams).setScale(1, RoundingMode.HALF_UP),
        BigDecimal.valueOf(cGrams).setScale(1, RoundingMode.HALF_UP),
        BigDecimal.valueOf(fGrams).setScale(1, RoundingMode.HALF_UP),
        pPct,
        cPct,
        fPct,
        isCustom);
  }

  public static int calculateWaterGoal(BigDecimal weightKg, Integer customWaterGoalMl) {
    if (customWaterGoalMl != null && customWaterGoalMl > 0) {
      return customWaterGoalMl;
    }
    // 35 ml per kg
    return (int) Math.round(weightKg.doubleValue() * 35.0);
  }
}
