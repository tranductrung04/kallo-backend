package com.healthcare.kallo.service.profile.impl;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.dto.request.profile.HealthProfileRequest;
import com.healthcare.kallo.dto.response.profile.HealthProfileResponse;
import com.healthcare.kallo.model.auth.User;
import com.healthcare.kallo.model.profile.UserHealthProfile;
import com.healthcare.kallo.repository.auth.UserRepository;
import com.healthcare.kallo.repository.profile.UserHealthProfileRepository;
import com.healthcare.kallo.service.profile.HealthProfileService;
import com.healthcare.kallo.service.profile.ProfileCalculator;
import com.healthcare.kallo.service.profile.ProfileCalculator.CalculatedProfileMetrics;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class HealthProfileServiceImpl implements HealthProfileService {

  private final UserHealthProfileRepository profileRepository;
  private final UserRepository userRepository;
  private final Clock clock;

  public HealthProfileServiceImpl(
      UserHealthProfileRepository profileRepository,
      UserRepository userRepository,
      Clock clock) {
    this.profileRepository = profileRepository;
    this.userRepository = userRepository;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  public HealthProfileResponse getProfile(UUID userId) {
    UserHealthProfile profile =
        profileRepository
            .findByUserId(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROFILE_NOT_FOUND));
    return toResponse(profile);
  }

  @Override
  public HealthProfileResponse upsertProfile(UUID userId, HealthProfileRequest request) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

    LocalDate today = LocalDate.now(clock);

    CalculatedProfileMetrics metrics =
        ProfileCalculator.calculate(
            request.gender(),
            request.birthDate(),
            today,
            request.heightCm(),
            request.weightKg(),
            request.activityLevel(),
            request.goalType(),
            request.weeklyRateKg(),
            request.customProteinPct(),
            request.customCarbsPct(),
            request.customFatPct(),
            request.customWaterGoalMl());

    UserHealthProfile profile =
        profileRepository.findByUserId(userId).orElseGet(UserHealthProfile::new);

    if (profile.getId() == null) {
      profile.setUser(user);
    }

    profile.setGender(request.gender());
    profile.setBirthDate(request.birthDate());
    profile.setHeightCm(request.heightCm());
    profile.setWeightKg(request.weightKg());
    profile.setActivityLevel(request.activityLevel());
    profile.setGoalType(request.goalType());
    profile.setTargetWeightKg(request.targetWeightKg());
    profile.setWeeklyRateKg(
        request.weeklyRateKg() != null ? request.weeklyRateKg() : BigDecimal.ZERO);

    profile.setBmi(metrics.bmi());
    profile.setBmiCategory(metrics.bmiCategory());
    profile.setBmr(metrics.bmr());
    profile.setTdee(metrics.tdee());
    profile.setDailyCalorieGoal(metrics.dailyCalorieGoal());
    profile.setSafetyClamped(metrics.safetyClamped());

    profile.setProteinGoalG(metrics.proteinGoalG());
    profile.setCarbsGoalG(metrics.carbsGoalG());
    profile.setFatGoalG(metrics.fatGoalG());
    profile.setProteinPct(metrics.proteinPct());
    profile.setCarbsPct(metrics.carbsPct());
    profile.setFatPct(metrics.fatPct());
    profile.setCustomMacro(metrics.customMacro());
    profile.setWaterGoalMl(metrics.waterGoalMl());

    if (request.allergies() != null) {
      profile.setAllergies(new ArrayList<>(request.allergies()));
    } else if (profile.getAllergies() == null) {
      profile.setAllergies(new ArrayList<>());
    }

    // Reset recalculation recommendation since user updated profile explicitly
    profile.setRecalculationRecommended(false);
    profile.setRecommendedTdee(null);
    profile.setRecommendedDailyCalorieGoal(null);
    profile.setLatestLoggedWeightKg(null);

    profile = profileRepository.save(profile);
    return toResponse(profile);
  }

  @Override
  public HealthProfileResponse recalculateWithLatestWeight(UUID userId) {
    UserHealthProfile profile =
        profileRepository
            .findByUserId(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROFILE_NOT_FOUND));

    BigDecimal newWeight = profile.getLatestLoggedWeightKg();
    if (newWeight == null) {
      // If no pending recommendation, simply recalculate with current weight
      newWeight = profile.getWeightKg();
    }

    LocalDate today = LocalDate.now(clock);

    BigDecimal customP = profile.isCustomMacro() ? profile.getProteinPct() : null;
    BigDecimal customC = profile.isCustomMacro() ? profile.getCarbsPct() : null;
    BigDecimal customF = profile.isCustomMacro() ? profile.getFatPct() : null;

    CalculatedProfileMetrics metrics =
        ProfileCalculator.calculate(
            profile.getGender(),
            profile.getBirthDate(),
            today,
            profile.getHeightCm(),
            newWeight,
            profile.getActivityLevel(),
            profile.getGoalType(),
            profile.getWeeklyRateKg(),
            customP,
            customC,
            customF,
            profile.getWaterGoalMl());

    profile.setWeightKg(newWeight);
    profile.setBmi(metrics.bmi());
    profile.setBmiCategory(metrics.bmiCategory());
    profile.setBmr(metrics.bmr());
    profile.setTdee(metrics.tdee());
    profile.setDailyCalorieGoal(metrics.dailyCalorieGoal());
    profile.setSafetyClamped(metrics.safetyClamped());
    profile.setProteinGoalG(metrics.proteinGoalG());
    profile.setCarbsGoalG(metrics.carbsGoalG());
    profile.setFatGoalG(metrics.fatGoalG());
    profile.setWaterGoalMl(metrics.waterGoalMl());

    profile.setRecalculationRecommended(false);
    profile.setRecommendedTdee(null);
    profile.setRecommendedDailyCalorieGoal(null);
    profile.setLatestLoggedWeightKg(null);

    profile = profileRepository.save(profile);
    return toResponse(profile);
  }

  private HealthProfileResponse toResponse(UserHealthProfile p) {
    LocalDate today = LocalDate.now(clock);
    int age = ProfileCalculator.calculateAge(p.getBirthDate(), today);
    return new HealthProfileResponse(
        p.getId(),
        p.getUser().getId(),
        p.getGender(),
        p.getBirthDate(),
        age,
        p.getHeightCm(),
        p.getWeightKg(),
        p.getActivityLevel(),
        p.getGoalType(),
        p.getTargetWeightKg(),
        p.getWeeklyRateKg(),
        p.getBmi(),
        p.getBmiCategory(),
        p.getBmiCategory().getDescriptionVi(),
        p.getBmr(),
        p.getTdee(),
        p.getDailyCalorieGoal(),
        p.isSafetyClamped(),
        p.getProteinGoalG(),
        p.getCarbsGoalG(),
        p.getFatGoalG(),
        p.getProteinPct(),
        p.getCarbsPct(),
        p.getFatPct(),
        p.isCustomMacro(),
        p.getWaterGoalMl(),
        p.getAllergies(),
        p.isRecalculationRecommended(),
        p.getRecommendedTdee(),
        p.getRecommendedDailyCalorieGoal(),
        p.getLatestLoggedWeightKg(),
        p.getCreatedAt(),
        p.getUpdatedAt());
  }
}
