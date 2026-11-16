package com.healthcare.kallo.service.game.impl;

import com.healthcare.kallo.event.game.BadgeEarnedEvent;
import com.healthcare.kallo.event.game.ChallengeCompletedEvent;
import com.healthcare.kallo.model.game.Badge;
import com.healthcare.kallo.model.game.Challenge;
import com.healthcare.kallo.model.sys.enums.NotificationType;
import com.healthcare.kallo.repository.game.BadgeRepository;
import com.healthcare.kallo.repository.game.ChallengeRepository;
import com.healthcare.kallo.service.game.GameNotifier;
import com.healthcare.kallo.service.sys.NotificationService;
import com.healthcare.kallo.service.sys.NotifyCommand;
import com.healthcare.kallo.service.sys.NotifyOutcome;
import com.healthcare.kallo.service.sys.RealtimePublisher;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GameNotifierImpl implements GameNotifier {
  private static final Logger log = LoggerFactory.getLogger(GameNotifierImpl.class);
  private final RealtimePublisher realtime;
  private final NotificationService notifications;
  private final BadgeRepository badges;
  private final ChallengeRepository challenges;

  public GameNotifierImpl(RealtimePublisher realtime, NotificationService notifications,
      BadgeRepository badges, ChallengeRepository challenges) {
    this.realtime = realtime;
    this.notifications = notifications;
    this.badges = badges;
    this.challenges = challenges;
  }

  @Override
  public void badgeEarned(BadgeEarnedEvent event) {
    Badge badge = badges.findById(event.badgeId()).orElse(null);
    Map<String, Object> badgePayload = new HashMap<>();
    badgePayload.put("id", event.badgeId());
    if (badge != null) {
      badgePayload.put("code", badge.getCode());
      badgePayload.put("name", badge.getName());
      badgePayload.put("iconKey", badge.getIconKey());
    }
    Map<String, Object> payload = Map.of("badge", badgePayload, "points", event.points());
    publish(event.userId(), "BADGE_EARNED", payload, NotificationType.BADGE_EARNED,
        Map.of("badgeId", event.badgeId()), "badge:" + event.badgeId());
  }

  @Override
  public void challengeCompleted(ChallengeCompletedEvent event) {
    Challenge challenge = challenges.findById(event.challengeId()).orElse(null);
    Map<String, Object> payload = new HashMap<>();
    payload.put("challengeId", event.challengeId());
    payload.put("title", challenge == null ? "Challenge" : challenge.getTitle());
    payload.put("pointsAwarded", event.pointsAwarded());
    payload.put("badge", event.badgeId());
    publish(event.userId(), "CHALLENGE_COMPLETED", payload, NotificationType.CHALLENGE_COMPLETED,
        Map.of("challengeId", event.challengeId()), "challenge:" + event.challengeId());
  }

  @Override
  public void challengeEndingSoon(UUID userId, UUID challengeId, String title, LocalDate endDate) {
    Map<String, Object> payload = Map.of("challengeId", challengeId, "title", title, "endDate", endDate);
    publish(userId, "CHALLENGE_ENDING_SOON", payload, NotificationType.CHALLENGE_EXPIRING,
        Map.of("challengeId", challengeId), "challenge-ending:" + challengeId + ":" + endDate);
  }

  private void publish(UUID userId, String realtimeType, Map<String, Object> payload,
      NotificationType notificationType, Map<String, Object> data, String dedupeKey) {
    NotifyOutcome outcome = null;
    try {
      outcome = notifications.notify(new NotifyCommand(userId, notificationType, payload,
          data.entrySet().stream().collect(java.util.stream.Collectors.toMap(
              Map.Entry::getKey, entry -> String.valueOf(entry.getValue()))), dedupeKey));
    } catch (RuntimeException exception) {
      log.warn("GAME notification persistence failed for {}", userId, exception);
    }
    if (outcome == NotifyOutcome.DUPLICATE) return;
    try {
      realtime.sendToUser(userId, "/queue/game-events", realtimeType, payload);
    } catch (RuntimeException exception) {
      log.warn("GAME realtime notification failed for {}", userId, exception);
    }
  }
}
