package com.healthcare.kallo.service.report;

import com.healthcare.kallo.event.act.ActivityDataChangedEvent;
import com.healthcare.kallo.event.act.WeightChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class DailySummaryEventListener {

  private static final Logger log = LoggerFactory.getLogger(DailySummaryEventListener.class);

  private final DailySummaryService dailySummaryService;

  public DailySummaryEventListener(DailySummaryService dailySummaryService) {
    this.dailySummaryService = dailySummaryService;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onActivityDataChanged(ActivityDataChangedEvent event) {
    try {
      dailySummaryService.recalculate(event.userId(), event.localDate());
    } catch (Exception ex) {
      log.warn("Failed to recalculate daily summary on activity data changed for user {} on date {}: {}",
          event.userId(), event.localDate(), ex.getMessage());
    }
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onWeightChanged(WeightChangedEvent event) {
    try {
      dailySummaryService.recalculate(event.userId(), event.localDate());
    } catch (Exception ex) {
      log.warn("Failed to recalculate daily summary on weight changed for user {} on date {}: {}",
          event.userId(), event.localDate(), ex.getMessage());
    }
  }
}
