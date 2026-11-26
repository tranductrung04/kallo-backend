package com.healthcare.kallo.service.nutri.listener;

import com.healthcare.kallo.event.act.ActivityDataChangedEvent;
import com.healthcare.kallo.event.profile.ProfileUpdatedEvent;
import com.healthcare.kallo.service.act.UserTimeResolver;
import com.healthcare.kallo.service.nutri.DailySummaryService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NutriExternalEventListener {

  private static final Logger log = LoggerFactory.getLogger(NutriExternalEventListener.class);

  private final DailySummaryService dailySummaryService;
  private final UserTimeResolver userTimeResolver;
  private final Clock clock;

  public NutriExternalEventListener(
      DailySummaryService dailySummaryService,
      UserTimeResolver userTimeResolver,
      Clock clock) {
    this.dailySummaryService = dailySummaryService;
    this.userTimeResolver = userTimeResolver;
    this.clock = clock;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onActivityDataChanged(ActivityDataChangedEvent event) {
    if (event == null || event.userId() == null || event.localDate() == null) {
      return;
    }
    try {
      dailySummaryService.recalculate(event.userId(), event.localDate());
    } catch (RuntimeException e) {
      log.warn(
          "Failed to recalculate daily summary after activity change for user {} on {}",
          event.userId(),
          event.localDate(),
          e);
    }
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onProfileUpdated(ProfileUpdatedEvent event) {
    if (event == null || event.userId() == null) {
      return;
    }
    try {
      ZoneId zone = userTimeResolver.zoneOf(event.userId());
      LocalDate today = userTimeResolver.localDate(Instant.now(clock), zone);
      dailySummaryService.recalculate(event.userId(), today);
    } catch (RuntimeException e) {
      log.warn(
          "Failed to recalculate daily summary after profile update for user {}",
          event.userId(),
          e);
    }
  }
}
