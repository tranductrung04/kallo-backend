package com.healthcare.kallo.service.game.impl;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.config.GameProperties;
import com.healthcare.kallo.dto.response.game.StreakResponse;
import com.healthcare.kallo.model.auth.User;
import com.healthcare.kallo.model.game.Streak;
import com.healthcare.kallo.repository.auth.UserRepository;
import com.healthcare.kallo.repository.game.StreakRepository;
import com.healthcare.kallo.service.game.DailyGoalPolicy;
import com.healthcare.kallo.service.game.DailyProgress;
import com.healthcare.kallo.service.game.DailyProgressProvider;
import com.healthcare.kallo.service.game.StreakCalculator;
import com.healthcare.kallo.service.game.StreakService;
import com.healthcare.kallo.service.game.UserDayResolver;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StreakServiceImpl implements StreakService {
  private final StreakRepository streaks;
  private final UserRepository users;
  private final DailyProgressProvider progress;
  private final DailyGoalPolicy goalPolicy;
  private final UserDayResolver days;
  private final GameProperties properties;
  private final StreakCalculator calculator;

  public StreakServiceImpl(StreakRepository streaks, UserRepository users, DailyProgressProvider progress,
      DailyGoalPolicy goalPolicy, UserDayResolver days, GameProperties properties, StreakCalculator calculator) {
    this.streaks = streaks;
    this.users = users;
    this.progress = progress;
    this.goalPolicy = goalPolicy;
    this.days = days;
    this.properties = properties;
    this.calculator = calculator;
  }

  @Override
  @Transactional
  public void recompute(UUID userId, LocalDate changedDate) {
    User user = user(userId);
    LocalDate today = days.today(user);
    LocalDate from = today.minusDays(properties.streak().windowDays() - 1L);
    Streak existing = streaks.findByUserIdForUpdate(userId).orElse(null);
    int storedLongest = existing == null ? 0 : existing.getLongestStreak();
    var metDates = new HashSet<LocalDate>();
    for (DailyProgress daily : progress.findRange(userId, from, today)) {
      if (daily != null && daily.localDate() != null && !daily.localDate().isBefore(from)
          && !daily.localDate().isAfter(today) && goalPolicy.isMet(daily)) {
        metDates.add(daily.localDate());
      }
    }
    StreakCalculator.Result result = calculator.calculate(metDates, today, storedLongest);
    streaks.upsert(userId, result.currentStreak(), result.longestStreak(), result.lastCompletedDate());
  }

  @Override
  @Transactional(readOnly = true)
  public StreakResponse get(UUID userId) {
    User user = user(userId);
    LocalDate today = days.today(user);
    Streak streak = streaks.findById(userId).orElse(null);
    if (streak == null) return empty();
    LocalDate last = streak.getLastCompletedDate();
    boolean todayCompleted = today.equals(last);
    boolean atRisk = today.minusDays(1).equals(last);
    int current = atRisk || todayCompleted ? streak.getCurrentStreak() : 0;
    return new StreakResponse(current, streak.getLongestStreak(), last, todayCompleted, atRisk);
  }

  private User user(UUID userId) {
    return users.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
  }

  private StreakResponse empty() {
    return new StreakResponse(0, 0, null, false, false);
  }
}
