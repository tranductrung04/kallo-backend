package com.healthcare.kallo.service.game;

import com.healthcare.kallo.model.game.UserChallenge;
import com.healthcare.kallo.model.game.enums.ChallengeStatus;
import com.healthcare.kallo.repository.game.UserChallengeRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ChallengeEndingSoonJob {
  private static final Logger log = LoggerFactory.getLogger(ChallengeEndingSoonJob.class);
  private static final int BATCH_SIZE = 500;
  private final UserChallengeRepository userChallenges;
  private final UserDayResolver days;
  private final GameNotifier notifier;

  public ChallengeEndingSoonJob(UserChallengeRepository userChallenges, UserDayResolver days,
      GameNotifier notifier) {
    this.userChallenges = userChallenges;
    this.days = days;
    this.notifier = notifier;
  }

  @Scheduled(cron = "0 0 9 * * *", zone = "UTC")
  public void notifyEndingSoon() {
    UUID after = null;
    while (true) {
      List<UserChallenge> active = after == null
          ? userChallenges.findByStatusWithChallenge(ChallengeStatus.ACTIVE,
              PageRequest.of(0, BATCH_SIZE))
          : userChallenges.findByStatusWithChallengeAfter(ChallengeStatus.ACTIVE, after,
              PageRequest.of(0, BATCH_SIZE));
      if (active.isEmpty()) return;
      for (UserChallenge participation : active) {
        try {
          LocalDate today = days.today(participation.getUser());
          LocalDate end = participation.getChallenge().getEndDate();
          if (!end.isBefore(today) && !end.isAfter(today.plusDays(1))) {
            notifier.challengeEndingSoon(participation.getUser().getId(),
                participation.getChallenge().getId(), participation.getChallenge().getTitle(), end);
          }
        } catch (RuntimeException exception) {
          log.warn("GAME challenge ending notification failed for {}", participation.getId(), exception);
        }
      }
      after = active.get(active.size() - 1).getId();
      if (active.size() < BATCH_SIZE) return;
    }
  }
}
