package com.healthcare.kallo.service.profile;

import com.healthcare.kallo.event.act.WeightChangedEvent;
import com.healthcare.kallo.model.act.WeightLog;
import com.healthcare.kallo.model.profile.UserHealthProfile;
import com.healthcare.kallo.repository.act.WeightLogRepository;
import com.healthcare.kallo.repository.profile.UserHealthProfileRepository;
import com.healthcare.kallo.service.profile.ProfileCalculator.CalorieResult;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ProfileWeightEventListener {

  private static final Logger log = LoggerFactory.getLogger(ProfileWeightEventListener.class);

  private final UserHealthProfileRepository profileRepository;
  private final WeightLogRepository weightLogRepository;
  private final Clock clock;

  public ProfileWeightEventListener(
      UserHealthProfileRepository profileRepository,
      WeightLogRepository weightLogRepository,
      Clock clock) {
    this.profileRepository = profileRepository;
    this.weightLogRepository = weightLogRepository;
    this.clock = clock;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onWeightChanged(WeightChangedEvent event) {
    if (event == null || event.userId() == null) {
      return;
    }

    Optional<UserHealthProfile> profileOpt = profileRepository.findByUserId(event.userId());
    if (profileOpt.isEmpty()) {
      return;
    }

    UserHealthProfile profile = profileOpt.get();

    Optional<WeightLog> latestLogOpt =
        weightLogRepository.findLatestAtOrBefore(event.userId(), Instant.now(clock));
    if (latestLogOpt.isEmpty()) {
      return;
    }

    BigDecimal latestWeight = latestLogOpt.get().getWeightKg();

    LocalDate today = LocalDate.now(clock);
    int age = ProfileCalculator.calculateAge(profile.getBirthDate(), today);
    int newBmr =
        ProfileCalculator.calculateBmr(
            profile.getGender(), latestWeight, profile.getHeightCm(), age);
    int newTdee = ProfileCalculator.calculateTdee(newBmr, profile.getActivityLevel());

    // FR-PRO-08: If TDEE change magnitude >= 100 kcal, suggest recalculation
    int diff = Math.abs(newTdee - profile.getTdee());
    if (diff >= 100) {
      CalorieResult calResult =
          ProfileCalculator.calculateDailyCalorieGoal(
              newTdee,
              newBmr,
              profile.getGender(),
              profile.getGoalType(),
              profile.getWeeklyRateKg());

      profile.setRecalculationRecommended(true);
      profile.setRecommendedTdee(newTdee);
      profile.setRecommendedDailyCalorieGoal(calResult.dailyCalorieGoal());
      profile.setLatestLoggedWeightKg(latestWeight);
      profileRepository.save(profile);

      log.info(
          "Triggered calorie recalculation recommendation for user {} (old TDEE: {}, new TDEE: {})",
          event.userId(),
          profile.getTdee(),
          newTdee);
    }
  }
}
