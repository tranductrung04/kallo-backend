package com.healthcare.kallo.service.game;

import com.healthcare.kallo.event.game.BadgeEarnedEvent;
import com.healthcare.kallo.event.game.ChallengeCompletedEvent;
import java.time.LocalDate;
import java.util.UUID;

public interface GameNotifier {
  void badgeEarned(BadgeEarnedEvent event);
  void challengeCompleted(ChallengeCompletedEvent event);
  void challengeEndingSoon(UUID userId, UUID challengeId, String title, LocalDate endDate);
}
