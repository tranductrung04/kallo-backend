package com.healthcare.kallo.service.game;

import com.healthcare.kallo.config.GameProperties;
import com.healthcare.kallo.repository.game.FriendshipRepository;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FriendshipCleanupJob {
  private static final Logger log = LoggerFactory.getLogger(FriendshipCleanupJob.class);
  private final FriendshipRepository friendships;
  private final Clock clock;
  private final GameProperties properties;

  public FriendshipCleanupJob(FriendshipRepository friendships, Clock clock, GameProperties properties) {
    this.friendships = friendships;
    this.clock = clock;
    this.properties = properties;
  }

  @Scheduled(cron = "0 0 4 * * *", zone = "UTC")
  public void deleteOldDeclinedRequests() {
    try {
      int deleted = friendships.deleteDeclinedBefore(
          Instant.now(clock).minus(properties.friend().declinedRetention()));
      log.info("Deleted {} old declined GAME friend requests", deleted);
    } catch (RuntimeException exception) {
      log.error("GAME declined friendship cleanup failed", exception);
    }
  }
}