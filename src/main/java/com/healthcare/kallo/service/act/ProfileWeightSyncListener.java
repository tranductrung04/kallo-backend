package com.healthcare.kallo.service.act;

import com.healthcare.kallo.event.profile.ProfileUpdatedEvent;
import com.healthcare.kallo.repository.act.WeightLogRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ProfileWeightSyncListener {

  private final WeightLogRepository weightLogRepository;

  public ProfileWeightSyncListener(WeightLogRepository weightLogRepository) {
    this.weightLogRepository = weightLogRepository;
  }

  @EventListener
  @Transactional
  public void onProfileUpdated(ProfileUpdatedEvent event) {
    if (event.userId() == null || event.weightKg() == null) {
      return;
    }

    Instant loggedAt = event.updatedAt() != null ? event.updatedAt() : Instant.now();
    UUID clientUuid = UUID.randomUUID();
    weightLogRepository.insertIfAbsent(
        event.userId(),
        clientUuid,
        event.weightKg(),
        loggedAt,
        "Profile baseline weight"
    );
  }
}
