package com.healthcare.kallo.service.profile;

import com.healthcare.kallo.model.profile.UserHealthProfile;
import com.healthcare.kallo.repository.profile.UserHealthProfileRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ProfileWeightChangeProcessor {

  private final UserHealthProfileRepository profileRepository;
  private final ProfileDomainService domainService;
  private final Clock clock;

  public ProfileWeightChangeProcessor(
      UserHealthProfileRepository profileRepository,
      ProfileDomainService domainService,
      Clock clock) {
    this.profileRepository = profileRepository;
    this.domainService = domainService;
    this.clock = clock;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void process(UUID userId, BigDecimal weightKg) {
    UserHealthProfile profile = profileRepository.findById(userId).orElse(null);
    if (profile == null) {
      return;
    }

    if (weightKg == null) {
      // User has deleted all weight logs
      profile.setLatestLoggedWeightKg(null);
      profile.setRecalculationRecommended(false);
      profile.setRecommendedTdee(null);
      profile.setRecommendedDailyCalorieGoal(null);
      profileRepository.saveAndFlush(profile);
      return;
    }

    profile.setLatestLoggedWeightKg(weightKg);

    LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("Asia/Ho_Chi_Minh")));
    int age = domainService.calculateAge(profile.getDob(), today);

    BigDecimal bmrNew = domainService.calculateBmr(
        weightKg,
        profile.getHeightCm(),
        age,
        profile.getGender()
    );
    BigDecimal tdeeNew = domainService.calculateTdee(bmrNew, profile.getActivityLevel());

    BigDecimal diff = tdeeNew.subtract(profile.getTdee()).abs();

    if (diff.compareTo(new BigDecimal("50")) >= 0) {
      profile.setRecalculationRecommended(true);
      profile.setRecommendedTdee(tdeeNew);

      // Calculate recommended daily calorie goal with new weight
      ProfileDomainService.ProfileMetrics recMetrics = domainService.calculateMetrics(
          weightKg,
          profile.getHeightCm(),
          age,
          profile.getGender(),
          profile.getActivityLevel(),
          profile.getGoalType(),
          profile.getTargetWeightKg(),
          profile.getWeeklyRateKg()
      );
      profile.setRecommendedDailyCalorieGoal(recMetrics.dailyCalorieGoal());
    } else {
      if (profile.isRecalculationRecommended()) {
        profile.setRecalculationRecommended(false);
        profile.setRecommendedTdee(null);
        profile.setRecommendedDailyCalorieGoal(null);
      }
    }

    profileRepository.saveAndFlush(profile);
  }
}
