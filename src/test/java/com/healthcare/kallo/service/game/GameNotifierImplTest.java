package com.healthcare.kallo.service.game;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.healthcare.kallo.event.game.BadgeEarnedEvent;
import com.healthcare.kallo.model.game.Badge;
import com.healthcare.kallo.repository.game.BadgeRepository;
import com.healthcare.kallo.repository.game.ChallengeRepository;
import com.healthcare.kallo.service.game.impl.GameNotifierImpl;
import com.healthcare.kallo.service.sys.NotificationService;
import com.healthcare.kallo.service.sys.RealtimePublisher;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class GameNotifierImplTest {
  @Test
  void publishesBadgeToRealtimeAndNotificationCenter() {
    RealtimePublisher realtime = Mockito.mock(RealtimePublisher.class);
    NotificationService notifications = Mockito.mock(NotificationService.class);
    BadgeRepository badges = Mockito.mock(BadgeRepository.class);
    ChallengeRepository challenges = Mockito.mock(ChallengeRepository.class);
    UUID userId = UUID.randomUUID();
    UUID badgeId = UUID.randomUUID();
    Badge badge = new Badge();
    badge.setId(badgeId);
    badge.setCode("STREAK_7");
    badge.setName("Seven");
    when(badges.findById(badgeId)).thenReturn(Optional.of(badge));

    new GameNotifierImpl(realtime, notifications, badges, challenges)
        .badgeEarned(new BadgeEarnedEvent(userId, badgeId, 20));

    verify(realtime).sendToUser(any(), org.mockito.ArgumentMatchers.eq("/queue/game-events"),
        org.mockito.ArgumentMatchers.eq("BADGE_EARNED"), any());
    verify(notifications).notify(any());
  }
}
