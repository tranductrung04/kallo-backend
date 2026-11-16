package com.healthcare.kallo.service.game;

import com.healthcare.kallo.config.GameProperties;
import com.healthcare.kallo.model.game.enums.ChallengeStatus;
import com.healthcare.kallo.repository.game.UserChallengeRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ChallengeExpiryJob {
  private static final Logger log = LoggerFactory.getLogger(ChallengeExpiryJob.class);
  private static final int BATCH_SIZE = 500;
  private final UserChallengeRepository userChallenges;
  private final ChallengeExpiryProcessor processor;
  private final Clock clock;
  private final GameProperties properties;

  public ChallengeExpiryJob(UserChallengeRepository userChallenges, ChallengeExpiryProcessor processor,
      Clock clock, GameProperties properties) {
    this.userChallenges = userChallenges;
    this.processor = processor;
    this.clock = clock;
    this.properties = properties;
  }

  @Scheduled(cron = "0 0 * * * *", zone = "UTC")
  public void expire() {
    // Candidate date uses configured application clock/UTC; each user's local-day refresh is resolved by ChallengeService.
    LocalDate cutoff = LocalDate.now(clock).minusDays(properties.challenge().expiryGrace().toDays());
    UUID after = null;
    while (true) {
      List<UUID> ids = after == null
          ? userChallenges.findExpiredIds(ChallengeStatus.ACTIVE, cutoff, PageRequest.of(0, BATCH_SIZE))
          : userChallenges.findExpiredIdsAfter(ChallengeStatus.ACTIVE, cutoff, after,
              PageRequest.of(0, BATCH_SIZE));
      if (ids.isEmpty()) return;
      for (UUID id : ids) {
        try {
          processor.expire(id);
        } catch (RuntimeException exception) {
          log.warn("GAME challenge expiry failed for {}", id, exception);
        }
      }
      after = ids.get(ids.size() - 1);
      if (ids.size() < BATCH_SIZE) return;
    }
  }

}
