package com.healthcare.kallo.service.profile;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.model.profile.enums.ActivityLevel;
import com.healthcare.kallo.model.profile.enums.Gender;
import com.healthcare.kallo.model.profile.enums.GoalType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import org.springframework.stereotype.Service;

@Service
public class ProfileDomainService {

  public static final BigDecimal MIN_SAFE_CALORIES_MALE = new BigDecimal("1500.00");
  public static final BigDecimal MIN_SAFE_CALORIES_FEMALE = new BigDecimal("1200.00");
  public static final BigDecimal MIN_BMI = new BigDecimal("18.5");
  public static final BigDecimal MAX_BMI_GAIN = new BigDecimal("35.0");
  public static final BigDecimal WATER_MULTIPLIER = new BigDecimal("0.035");
  public static final BigDecimal PROTEIN_MULTIPLIER = new BigDecimal("2.0");
  public static final BigDecimal FAT_CALORIE_RATIO = new BigDecimal("0.25");
  public static final BigDecimal KCAL_PER_KG_FAT = new BigDecimal("7700");
  public static final BigDecimal DAYS_PER_WEEK = new BigDecimal("7");
  public static final BigDecimal KCAL_PER_GRAM_FAT = new BigDecimal("9");
  public static final BigDecimal KCAL_PER_GRAM_PROTEIN = new BigDecimal("4");
  public static final BigDecimal KCAL_PER_GRAM_CARB = new BigDecimal("4");
  public static final BigDecimal MAX_WEEKLY_RATE = new BigDecimal("1.50");

  public record ProfileMetrics(
      int age,
      BigDecimal bmi,
      BigDecimal bmr,
      BigDecimal tdee,
      BigDecimal dailyCalorieGoal,
      BigDecimal proteinGrams,
      BigDecimal fatGrams,
      BigDecimal carbGrams,
      BigDecimal waterLiters,
      boolean rateClamped,
      BigDecimal effectiveWeeklyRateKg
  ) {}

  public int calculateAge(LocalDate dob, LocalDate today) {
    if (dob == null || today == null) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR);
    }
    return Period.between(dob, today).getYears();
  }

  public BigDecimal calculateBmi(BigDecimal weightKg, BigDecimal heightCm) {
    BigDecimal heightM = heightCm.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
    BigDecimal heightM2 = heightM.multiply(heightM);
    return weightKg.divide(heightM2, 2, RoundingMode.HALF_UP);
  }

  public BigDecimal calculateBmr(BigDecimal weightKg, BigDecimal heightCm, int age, Gender gender) {
    // Mifflin-St Jeor: 10 * W + 6.25 * H - 5 * A + s
    BigDecimal termW = new BigDecimal("10").multiply(weightKg);
    BigDecimal termH = new BigDecimal("6.25").multiply(heightCm);
    BigDecimal termA = new BigDecimal("5").multiply(new BigDecimal(age));
    BigDecimal s = (gender == Gender.MALE) ? new BigDecimal("5") : new BigDecimal("-161");

    return termW.add(termH).subtract(termA).add(s).setScale(2, RoundingMode.HALF_UP);
  }

  public BigDecimal calculateTdee(BigDecimal bmr, ActivityLevel activityLevel) {
    return bmr.multiply(activityLevel.getPal()).setScale(2, RoundingMode.HALF_UP);
  }

  public BigDecimal calculateWaterLiters(BigDecimal weightKg) {
    return weightKg.multiply(WATER_MULTIPLIER).setScale(2, RoundingMode.HALF_UP);
  }

  public void validateSafetyRules(
      int age,
      BigDecimal weightKg,
      BigDecimal heightCm,
      Gender gender,
      GoalType goalType,
      BigDecimal targetWeightKg,
      BigDecimal weeklyRateKg
  ) {
    // Rule 1: Age < 18 only MAINTAIN_WEIGHT allowed
    if (age < 18 && goalType != GoalType.MAINTAIN_WEIGHT) {
      throw new BusinessException(ErrorCode.UNDERAGE_GOAL_RESTRICTION);
    }

    // Rule 2: BMI check
    BigDecimal currentBmi = calculateBmi(weightKg, heightCm);
    if (currentBmi.compareTo(MIN_BMI) < 0 && goalType == GoalType.LOSE_WEIGHT) {
      throw new BusinessException(ErrorCode.UNSAFE_TARGET_BMI);
    }

    if (goalType == GoalType.MAINTAIN_WEIGHT) {
      if (weeklyRateKg != null && weeklyRateKg.compareTo(BigDecimal.ZERO) != 0) {
        throw new BusinessException(ErrorCode.INVALID_WEEKLY_RATE);
      }
    } else {
      if (targetWeightKg == null) {
        throw new BusinessException(ErrorCode.VALIDATION_ERROR);
      }
      if (weeklyRateKg == null || weeklyRateKg.compareTo(BigDecimal.ZERO) <= 0 || weeklyRateKg.compareTo(MAX_WEEKLY_RATE) > 0) {
        throw new BusinessException(ErrorCode.INVALID_WEEKLY_RATE);
      }

      BigDecimal targetBmi = calculateBmi(targetWeightKg, heightCm);
      if (goalType == GoalType.LOSE_WEIGHT && targetBmi.compareTo(MIN_BMI) < 0) {
        throw new BusinessException(ErrorCode.UNSAFE_TARGET_BMI);
      }
      if (goalType == GoalType.GAIN_WEIGHT && targetBmi.compareTo(MAX_BMI_GAIN) > 0) {
        throw new BusinessException(ErrorCode.UNSAFE_TARGET_BMI);
      }
    }
  }

  public ProfileMetrics calculateMetrics(
      BigDecimal weightKg,
      BigDecimal heightCm,
      int age,
      Gender gender,
      ActivityLevel activityLevel,
      GoalType goalType,
      BigDecimal targetWeightKg,
      BigDecimal weeklyRateKg
  ) {
    validateSafetyRules(age, weightKg, heightCm, gender, goalType, targetWeightKg, weeklyRateKg);

    BigDecimal bmi = calculateBmi(weightKg, heightCm);
    BigDecimal bmr = calculateBmr(weightKg, heightCm, age, gender);
    BigDecimal tdee = calculateTdee(bmr, activityLevel);

    BigDecimal effectiveRate = BigDecimal.ZERO;
    boolean rateClamped = false;
    BigDecimal dailyCalorieGoal;

    if (goalType == GoalType.MAINTAIN_WEIGHT) {
      dailyCalorieGoal = tdee;
    } else {
      BigDecimal maxAllowedRate = weightKg.multiply(new BigDecimal("0.01")).setScale(2, RoundingMode.HALF_UP);
      if (weeklyRateKg != null && weeklyRateKg.compareTo(maxAllowedRate) > 0) {
        rateClamped = true;
        effectiveRate = maxAllowedRate;
      } else {
        effectiveRate = weeklyRateKg != null ? weeklyRateKg : BigDecimal.ZERO;
      }

      BigDecimal dailyDelta = effectiveRate.multiply(KCAL_PER_KG_FAT)
          .divide(DAYS_PER_WEEK, 4, RoundingMode.HALF_UP);

      if (goalType == GoalType.LOSE_WEIGHT) {
        BigDecimal rawCalorieGoal = tdee.subtract(dailyDelta);
        BigDecimal minSafe = (gender == Gender.MALE) ? MIN_SAFE_CALORIES_MALE : MIN_SAFE_CALORIES_FEMALE;

        if (tdee.compareTo(minSafe) <= 0) {
          // If TDEE <= MinSafe: MAX(TDEE - delta, BMR)
          dailyCalorieGoal = rawCalorieGoal.max(bmr);
        } else {
          // If TDEE > MinSafe: MAX(rawCalorieGoal, minSafe)
          dailyCalorieGoal = rawCalorieGoal.max(minSafe);
        }
      } else { // GAIN_WEIGHT
        dailyCalorieGoal = tdee.add(dailyDelta);
      }
    }

    dailyCalorieGoal = dailyCalorieGoal.setScale(2, RoundingMode.HALF_UP);

    // Protein: 2.0 g / kg body weight
    BigDecimal proteinGrams = weightKg.multiply(PROTEIN_MULTIPLIER).setScale(2, RoundingMode.HALF_UP);

    // Fat: 25% of daily calorie goal, 9 kcal / gram
    BigDecimal fatKcal = dailyCalorieGoal.multiply(FAT_CALORIE_RATIO);
    BigDecimal fatGrams = fatKcal.divide(KCAL_PER_GRAM_FAT, 2, RoundingMode.HALF_UP);

    // Carb: remaining calories, 4 kcal / gram
    BigDecimal proteinKcal = proteinGrams.multiply(KCAL_PER_GRAM_PROTEIN);
    BigDecimal fatActualKcal = fatGrams.multiply(KCAL_PER_GRAM_FAT);
    BigDecimal carbKcal = dailyCalorieGoal.subtract(proteinKcal.add(fatActualKcal));
    BigDecimal carbGrams = carbKcal.compareTo(BigDecimal.ZERO) > 0
        ? carbKcal.divide(KCAL_PER_GRAM_CARB, 2, RoundingMode.HALF_UP)
        : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    // Water: W * 0.035
    BigDecimal waterLiters = calculateWaterLiters(weightKg);

    return new ProfileMetrics(
        age,
        bmi,
        bmr,
        tdee,
        dailyCalorieGoal,
        proteinGrams,
        fatGrams,
        carbGrams,
        waterLiters,
        rateClamped,
        effectiveRate
    );
  }
}
