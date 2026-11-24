package com.healthcare.kallo.service.profile;

import com.healthcare.kallo.event.act.WeightChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class WeightChangedEventListener {

  private static final Logger log = LoggerFactory.getLogger(WeightChangedEventListener.class);
  private static final int MAX_ATTEMPTS = 3;

  private final ProfileWeightChangeProcessor processor;

  public WeightChangedEventListener(ProfileWeightChangeProcessor processor) {
    this.processor = processor;
  }

  @EventListener
  public void onWeightChanged(WeightChangedEvent event) {
    if (event.userId() == null) {
      return;
    }

    // If event was triggered without weight (e.g. legacy event), skip or do nothing
    // When weight is present (or explicitly null after deletion), process it
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      try {
        processor.process(event.userId(), event.weightKg());
        break;
      } catch (OptimisticLockingFailureException ex) {
        log.warn("Optimistic lock failure processing WeightChangedEvent for user {}, attempt {}/{}",
            event.userId(), attempt, MAX_ATTEMPTS);
        if (attempt == MAX_ATTEMPTS) {
          log.error("Exhausted retries for WeightChangedEvent for user {}", event.userId(), ex);
          throw ex;
        }
        try {
          Thread.sleep(50L * (1L << (attempt - 1)));
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw ex;
        }
      }
    }
  }
}
