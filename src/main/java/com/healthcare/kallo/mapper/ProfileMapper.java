package com.healthcare.kallo.mapper;

import com.healthcare.kallo.dto.response.profile.MacronutrientDto;
import com.healthcare.kallo.dto.response.profile.ProfileResponseDto;
import com.healthcare.kallo.model.profile.UserHealthProfile;
import com.healthcare.kallo.service.profile.ProfileDomainService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

@Component
public class ProfileMapper {

  private final ProfileDomainService domainService;
  private final Clock clock;

  public ProfileMapper(ProfileDomainService domainService, Clock clock) {
    this.domainService = domainService;
    this.clock = clock;
  }

  public ProfileResponseDto toResponse(UserHealthProfile profile, String goalAdvice) {
    if (profile == null) {
      return null;
    }
    LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("Asia/Ho_Chi_Minh")));
    int age = Period.between(profile.getDob(), today).getYears();
    BigDecimal bmi = domainService.calculateBmi(profile.getWeightKg(), profile.getHeightCm());

    MacronutrientDto macronutrients = new MacronutrientDto(
        profile.getProteinGrams(),
        profile.getFatGrams(),
        profile.getCarbGrams()
    );

    return new ProfileResponseDto(
        profile.getUserId(),
        profile.getGender(),
        profile.getDob(),
        age,
        profile.getHeightCm(),
        profile.getWeightKg(),
        bmi,
        profile.getActivityLevel(),
        profile.getGoalType(),
        profile.getTargetWeightKg(),
        profile.getWeeklyRateKg(),
        profile.getBmr(),
        profile.getTdee(),
        profile.getDailyCalorieGoal(),
        macronutrients,
        profile.getWaterLiters(),
        profile.isRateClamped(),
        profile.isRecalculationRecommended(),
        profile.getRecommendedTdee(),
        profile.getRecommendedDailyCalorieGoal(),
        profile.getLatestLoggedWeightKg(),
        goalAdvice,
        profile.getVersion()
    );
  }

  public ProfileResponseDto toResponse(UserHealthProfile profile) {
    return toResponse(profile, null);
  }
}
