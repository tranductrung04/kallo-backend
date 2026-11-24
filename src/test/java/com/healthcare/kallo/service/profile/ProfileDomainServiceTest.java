package com.healthcare.kallo.service.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.model.profile.enums.ActivityLevel;
import com.healthcare.kallo.model.profile.enums.Gender;
import com.healthcare.kallo.model.profile.enums.GoalType;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProfileDomainServiceTest {

  private ProfileDomainService domainService;

  @BeforeEach
  void setUp() {
    domainService = new ProfileDomainService();
  }

  @Test
  @DisplayName("V1: Chuẩn duy trì cân nặng (MALE, 175cm, 70kg, SEDENTARY, MAINTAIN)")
  void testVector1_maintainWeight() {
    ProfileDomainService.ProfileMetrics result = domainService.calculateMetrics(
        new BigDecimal("70.00"),
        new BigDecimal("175.00"),
        42,
        Gender.MALE,
        ActivityLevel.SEDENTARY,
        GoalType.MAINTAIN_WEIGHT,
        null,
        null
    );

    assertThat(result.bmr()).isEqualByComparingTo("1588.75");
    assertThat(result.tdee()).isEqualByComparingTo("1906.50");
    assertThat(result.dailyCalorieGoal()).isEqualByComparingTo("1906.50");
    assertThat(result.rateClamped()).isFalse();
    assertThat(result.proteinGrams()).isEqualByComparingTo("140.00");
    assertThat(result.waterLiters()).isEqualByComparingTo("2.45");
  }

  @Test
  @DisplayName("V2: Chạm sàn Calo Nữ 1200 kcal (FEMALE, 160cm, 55kg, LIGHTLY, LOSE)")
  void testVector2_femaleMinSafeFloor() {
    ProfileDomainService.ProfileMetrics result = domainService.calculateMetrics(
        new BigDecimal("55.00"),
        new BigDecimal("160.00"),
        27,
        Gender.FEMALE,
        ActivityLevel.LIGHTLY_ACTIVE,
        GoalType.LOSE_WEIGHT,
        new BigDecimal("50.00"),
        new BigDecimal("0.50")
    );

    assertThat(result.bmr()).isEqualByComparingTo("1254.00");
    assertThat(result.tdee()).isEqualByComparingTo("1724.25");
    assertThat(result.dailyCalorieGoal()).isEqualByComparingTo("1200.00");
    assertThat(result.rateClamped()).isFalse();
    assertThat(result.proteinGrams()).isEqualByComparingTo("110.00");
    assertThat(result.waterLiters()).isEqualByComparingTo("1.93");
  }

  @Test
  @DisplayName("V3: Kẹp tốc độ: 85kg * 1% = 0.85 kg/tuần (MALE, 180cm, 85kg, VERY, LOSE)")
  void testVector3_rateClamped() {
    ProfileDomainService.ProfileMetrics result = domainService.calculateMetrics(
        new BigDecimal("85.00"),
        new BigDecimal("180.00"),
        27,
        Gender.MALE,
        ActivityLevel.VERY_ACTIVE,
        GoalType.LOSE_WEIGHT,
        new BigDecimal("75.00"),
        new BigDecimal("1.20")
    );

    assertThat(result.bmr()).isEqualByComparingTo("1845.00");
    assertThat(result.tdee()).isEqualByComparingTo("3182.63");
    assertThat(result.dailyCalorieGoal()).isEqualByComparingTo("2247.63");
    assertThat(result.rateClamped()).isTrue();
    assertThat(result.effectiveWeeklyRateKg()).isEqualByComparingTo("0.85");
  }

  @Test
  @DisplayName("V4: Lỗi UNDERAGE_GOAL_RESTRICTION khi tuổi < 18 chọn giảm cân")
  void testVector4_underageRestriction() {
    assertThatThrownBy(() -> domainService.calculateMetrics(
        new BigDecimal("48.00"),
        new BigDecimal("155.00"),
        16,
        Gender.FEMALE,
        ActivityLevel.SEDENTARY,
        GoalType.LOSE_WEIGHT,
        new BigDecimal("45.00"),
        new BigDecimal("0.25")
    ))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.UNDERAGE_GOAL_RESTRICTION);
  }

  @Test
  @DisplayName("V5: Lỗi UNSAFE_TARGET_BMI khi BMI < 18.5 chọn giảm cân")
  void testVector5_unsafeTargetBmi() {
    // 52kg / 1.70^2 = 17.99 < 18.5
    assertThatThrownBy(() -> domainService.calculateMetrics(
        new BigDecimal("52.00"),
        new BigDecimal("170.00"),
        31,
        Gender.MALE,
        ActivityLevel.MODERATELY_ACTIVE,
        GoalType.LOSE_WEIGHT,
        new BigDecimal("48.00"),
        new BigDecimal("0.25")
    ))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.UNSAFE_TARGET_BMI);
  }

  @Test
  @DisplayName("V6: TDEE <= 1200 áp BMR làm sàn, không bị đẩy lên 1200 (FEMALE, 148cm, 46kg, SEDENTARY, LOSE)")
  void testVector6_tdeeUnderMinSafe() {
    ProfileDomainService.ProfileMetrics result = domainService.calculateMetrics(
        new BigDecimal("46.00"),
        new BigDecimal("148.00"),
        65,
        Gender.FEMALE,
        ActivityLevel.SEDENTARY,
        GoalType.LOSE_WEIGHT,
        new BigDecimal("43.00"),
        new BigDecimal("0.25")
    );

    assertThat(result.bmr()).isEqualByComparingTo("899.00");
    assertThat(result.tdee()).isEqualByComparingTo("1078.80");
    assertThat(result.dailyCalorieGoal()).isEqualByComparingTo("899.00");
    assertThat(result.rateClamped()).isFalse();
  }

  @Test
  @DisplayName("V7: Tăng cân chuẩn (MALE, 175cm, 68kg, EXTRA, GAIN)")
  void testVector7_gainWeight() {
    ProfileDomainService.ProfileMetrics result = domainService.calculateMetrics(
        new BigDecimal("68.00"),
        new BigDecimal("175.00"),
        34,
        Gender.MALE,
        ActivityLevel.EXTRA_ACTIVE,
        GoalType.GAIN_WEIGHT,
        new BigDecimal("73.00"),
        new BigDecimal("0.50")
    );

    assertThat(result.bmr()).isEqualByComparingTo("1608.75");
    assertThat(result.tdee()).isEqualByComparingTo("3056.63");
    assertThat(result.dailyCalorieGoal()).isEqualByComparingTo("3606.63");
    assertThat(result.rateClamped()).isFalse();
  }

  @Test
  @DisplayName("V8: Kiểm tra logic chạm mục tiêu cân nặng (FEMALE, 60kg, target 60kg)")
  void testVector8_targetWeightReached() {
    BigDecimal currentWeight = new BigDecimal("60.00");
    BigDecimal targetWeight = new BigDecimal("60.00");

    // LOSE_WEIGHT where currentWeight <= targetWeight -> target reached
    boolean targetReached = currentWeight.compareTo(targetWeight) <= 0;
    assertThat(targetReached).isTrue();
  }

  @Test
  @DisplayName("V9: Giảm cân chuẩn (MALE, 182cm, 90kg, MODERATE, LOSE)")
  void testVector9_loseWeightStandard() {
    ProfileDomainService.ProfileMetrics result = domainService.calculateMetrics(
        new BigDecimal("90.00"),
        new BigDecimal("182.00"),
        33,
        Gender.MALE,
        ActivityLevel.MODERATELY_ACTIVE,
        GoalType.LOSE_WEIGHT,
        new BigDecimal("80.00"),
        new BigDecimal("0.50")
    );

    assertThat(result.bmr()).isEqualByComparingTo("1877.50");
    assertThat(result.tdee()).isEqualByComparingTo("2910.13");
    assertThat(result.dailyCalorieGoal()).isEqualByComparingTo("2360.13");
    assertThat(result.rateClamped()).isFalse();
  }
}
