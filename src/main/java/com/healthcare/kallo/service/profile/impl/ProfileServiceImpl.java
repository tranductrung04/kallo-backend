package com.healthcare.kallo.service.profile.impl;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.dto.request.profile.ProfileRequestDto;
import com.healthcare.kallo.dto.response.profile.ProfileResponseDto;
import com.healthcare.kallo.event.profile.ProfileUpdatedEvent;
import com.healthcare.kallo.mapper.ProfileMapper;
import com.healthcare.kallo.model.profile.UserHealthProfile;
import com.healthcare.kallo.model.profile.enums.GoalType;
import com.healthcare.kallo.repository.auth.UserRepository;
import com.healthcare.kallo.repository.profile.UserHealthProfileRepository;
import com.healthcare.kallo.service.profile.ProfileDomainService;
import com.healthcare.kallo.service.profile.ProfileService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileServiceImpl implements ProfileService {

  private final UserHealthProfileRepository profileRepository;
  private final UserRepository userRepository;
  private final ProfileDomainService domainService;
  private final ProfileMapper mapper;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  public ProfileServiceImpl(
      UserHealthProfileRepository profileRepository,
      UserRepository userRepository,
      ProfileDomainService domainService,
      ProfileMapper mapper,
      ApplicationEventPublisher events,
      Clock clock) {
    this.profileRepository = profileRepository;
    this.userRepository = userRepository;
    this.domainService = domainService;
    this.mapper = mapper;
    this.events = events;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  public ProfileResponseDto getProfile(UUID userId) {
    UserHealthProfile profile = profileRepository.findById(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.PROFILE_NOT_FOUND));
    return mapper.toResponse(profile);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean existsByUserId(UUID userId) {
    return profileRepository.existsById(userId);
  }

  @Override
  @Transactional
  public ProfileResponseDto createProfile(UUID userId, ProfileRequestDto request) {
    if (!userRepository.existsById(userId)) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }

    LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("Asia/Ho_Chi_Minh")));
    int age = domainService.calculateAge(request.dob(), today);

    ProfileDomainService.ProfileMetrics metrics = domainService.calculateMetrics(
        request.weightKg(),
        request.heightCm(),
        age,
        request.gender(),
        request.activityLevel(),
        request.goalType(),
        request.targetWeightKg(),
        request.weeklyRateKg()
    );

    UserHealthProfile profile = new UserHealthProfile();
    profile.setUserId(userId);
    profile.setGender(request.gender());
    profile.setDob(request.dob());
    profile.setHeightCm(request.heightCm());
    profile.setWeightKg(request.weightKg());
    profile.setActivityLevel(request.activityLevel());
    profile.setGoalType(request.goalType());
    profile.setTargetWeightKg(request.goalType() == GoalType.MAINTAIN_WEIGHT ? null : request.targetWeightKg());
    profile.setWeeklyRateKg(metrics.effectiveWeeklyRateKg());
    profile.setBmr(metrics.bmr());
    profile.setTdee(metrics.tdee());
    profile.setDailyCalorieGoal(metrics.dailyCalorieGoal());
    profile.setCarbGrams(metrics.carbGrams());
    profile.setProteinGrams(metrics.proteinGrams());
    profile.setFatGrams(metrics.fatGrams());
    profile.setWaterLiters(metrics.waterLiters());
    profile.setStepGoal(request.stepGoal());
    profile.setRateClamped(metrics.rateClamped());
    profile.setRecalculationRecommended(false);
    profile.setRecommendedTdee(null);
    profile.setRecommendedDailyCalorieGoal(null);
    profile.setLatestLoggedWeightKg(request.weightKg());

    UserHealthProfile saved = profileRepository.saveAndFlush(profile);
    events.publishEvent(new ProfileUpdatedEvent(
        userId,
        saved.getWeightKg(),
        saved.getUpdatedAt() != null ? saved.getUpdatedAt() : Instant.now(clock)
    ));
    return mapper.toResponse(saved);
  }

  @Override
  @Transactional
  public ProfileResponseDto updateProfile(UUID userId, ProfileRequestDto request) {
    UserHealthProfile profile = profileRepository.findById(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.PROFILE_NOT_FOUND));

    LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("Asia/Ho_Chi_Minh")));
    int age = domainService.calculateAge(request.dob(), today);

    ProfileDomainService.ProfileMetrics metrics = domainService.calculateMetrics(
        request.weightKg(),
        request.heightCm(),
        age,
        request.gender(),
        request.activityLevel(),
        request.goalType(),
        request.targetWeightKg(),
        request.weeklyRateKg()
    );

    profile.setGender(request.gender());
    profile.setDob(request.dob());
    profile.setHeightCm(request.heightCm());
    profile.setWeightKg(request.weightKg());
    profile.setActivityLevel(request.activityLevel());
    profile.setGoalType(request.goalType());
    profile.setTargetWeightKg(request.goalType() == GoalType.MAINTAIN_WEIGHT ? null : request.targetWeightKg());
    profile.setWeeklyRateKg(metrics.effectiveWeeklyRateKg());
    profile.setBmr(metrics.bmr());
    profile.setTdee(metrics.tdee());
    profile.setDailyCalorieGoal(metrics.dailyCalorieGoal());
    profile.setCarbGrams(metrics.carbGrams());
    profile.setProteinGrams(metrics.proteinGrams());
    profile.setFatGrams(metrics.fatGrams());
    profile.setWaterLiters(metrics.waterLiters());
    if (request.stepGoal() != null) {
      profile.setStepGoal(request.stepGoal());
    }
    profile.setRateClamped(metrics.rateClamped());
    profile.setRecalculationRecommended(false);
    profile.setRecommendedTdee(null);
    profile.setRecommendedDailyCalorieGoal(null);

    UserHealthProfile saved = profileRepository.saveAndFlush(profile);
    events.publishEvent(new ProfileUpdatedEvent(
        userId,
        saved.getWeightKg(),
        saved.getUpdatedAt() != null ? saved.getUpdatedAt() : Instant.now(clock)
    ));
    return mapper.toResponse(saved);
  }

  @Override
  @Transactional
  public ProfileResponseDto recalculate(UUID userId) {
    UserHealthProfile profile = profileRepository.findById(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.PROFILE_NOT_FOUND));

    BigDecimal newWeight = profile.getLatestLoggedWeightKg() != null
        ? profile.getLatestLoggedWeightKg()
        : profile.getWeightKg();

    String goalAdvice = null;
    GoalType goalType = profile.getGoalType();
    BigDecimal targetWeight = profile.getTargetWeightKg();
    BigDecimal weeklyRate = profile.getWeeklyRateKg();

    // Check if target reached
    if (goalType == GoalType.LOSE_WEIGHT && targetWeight != null && newWeight.compareTo(targetWeight) <= 0) {
      goalType = GoalType.MAINTAIN_WEIGHT;
      targetWeight = null;
      weeklyRate = BigDecimal.ZERO;
      goalAdvice = "TARGET_REACHED";
    } else if (goalType == GoalType.GAIN_WEIGHT && targetWeight != null && newWeight.compareTo(targetWeight) >= 0) {
      goalType = GoalType.MAINTAIN_WEIGHT;
      targetWeight = null;
      weeklyRate = BigDecimal.ZERO;
      goalAdvice = "TARGET_REACHED";
    }

    LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("Asia/Ho_Chi_Minh")));
    int age = domainService.calculateAge(profile.getDob(), today);

    ProfileDomainService.ProfileMetrics metrics = domainService.calculateMetrics(
        newWeight,
        profile.getHeightCm(),
        age,
        profile.getGender(),
        profile.getActivityLevel(),
        goalType,
        targetWeight,
        weeklyRate
    );

    profile.setWeightKg(newWeight);
    profile.setGoalType(goalType);
    profile.setTargetWeightKg(targetWeight);
    profile.setWeeklyRateKg(metrics.effectiveWeeklyRateKg());
    profile.setBmr(metrics.bmr());
    profile.setTdee(metrics.tdee());
    profile.setDailyCalorieGoal(metrics.dailyCalorieGoal());
    profile.setCarbGrams(metrics.carbGrams());
    profile.setProteinGrams(metrics.proteinGrams());
    profile.setFatGrams(metrics.fatGrams());
    profile.setWaterLiters(metrics.waterLiters());
    profile.setRateClamped(metrics.rateClamped());
    profile.setRecalculationRecommended(false);
    profile.setRecommendedTdee(null);
    profile.setRecommendedDailyCalorieGoal(null);

    UserHealthProfile saved = profileRepository.saveAndFlush(profile);
    events.publishEvent(new ProfileUpdatedEvent(
        userId,
        saved.getWeightKg(),
        saved.getUpdatedAt() != null ? saved.getUpdatedAt() : Instant.now(clock)
    ));
    return mapper.toResponse(saved, goalAdvice);
  }
}
