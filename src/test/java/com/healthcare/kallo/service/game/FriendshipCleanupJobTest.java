package com.healthcare.kallo.service.game;

import static org.mockito.Mockito.verify;

import com.healthcare.kallo.config.GameProperties;
import com.healthcare.kallo.config.GamePropertiesFixture;
import com.healthcare.kallo.repository.game.FriendshipRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class FriendshipCleanupJobTest {
  @Test
  void deletesDeclinedRequestsOlderThanThirtyDays() {
    FriendshipRepository repository = org.mockito.Mockito.mock(FriendshipRepository.class);
    Clock clock = Clock.fixed(Instant.parse("2026-11-16T00:00:00Z"), ZoneOffset.UTC);
    FriendshipCleanupJob job = new FriendshipCleanupJob(repository, clock, properties());

    job.deleteOldDeclinedRequests();

    verify(repository).deleteDeclinedBefore(Instant.parse("2026-10-17T00:00:00Z"));
  }

  private GameProperties properties() {
    return GamePropertiesFixture.defaults();
  }
}
