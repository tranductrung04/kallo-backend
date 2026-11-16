package com.healthcare.kallo.service.game;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.healthcare.kallo.model.auth.User;
import com.healthcare.kallo.model.game.Challenge;
import com.healthcare.kallo.model.game.UserChallenge;
import com.healthcare.kallo.model.game.enums.ChallengeStatus;
import com.healthcare.kallo.repository.game.UserChallengeRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.PageRequest;

class ChallengeEndingSoonJobTest {
  @Test
  void continuesAfterOneParticipationFails() {
    UserChallengeRepository participations = Mockito.mock(UserChallengeRepository.class);
    UserDayResolver days = Mockito.mock(UserDayResolver.class);
    GameNotifier notifier = Mockito.mock(GameNotifier.class);
    UserChallenge broken = participation("UTC", LocalDate.of(2026, 11, 16));
    UserChallenge valid = participation("UTC", LocalDate.of(2026, 11, 17));
    when(participations.findByStatusWithChallenge(ChallengeStatus.ACTIVE, PageRequest.of(0, 500)))
        .thenReturn(List.of(broken, valid));
    when(days.today(broken.getUser())).thenThrow(new IllegalArgumentException("timezone"));
    when(days.today(valid.getUser())).thenReturn(LocalDate.of(2026, 11, 16));

    new ChallengeEndingSoonJob(participations, days, notifier).notifyEndingSoon();

    verify(notifier).challengeEndingSoon(valid.getUser().getId(), valid.getChallenge().getId(),
        valid.getChallenge().getTitle(), valid.getChallenge().getEndDate());
  }

  @Test
  void ignoresInactiveChallengeThroughRepositoryQueryContract() {
    UserChallengeRepository participations = Mockito.mock(UserChallengeRepository.class);
    UserDayResolver days = Mockito.mock(UserDayResolver.class);
    GameNotifier notifier = Mockito.mock(GameNotifier.class);
    when(participations.findByStatusWithChallenge(ChallengeStatus.ACTIVE, PageRequest.of(0, 500)))
        .thenReturn(List.of());

    new ChallengeEndingSoonJob(participations, days, notifier).notifyEndingSoon();

    verify(notifier, never()).challengeEndingSoon(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
  }

  private UserChallenge participation(String timezone, LocalDate endDate) {
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setTimezone(timezone);
    Challenge challenge = new Challenge();
    challenge.setId(UUID.randomUUID());
    challenge.setTitle("Challenge");
    challenge.setEndDate(endDate);
    UserChallenge value = new UserChallenge();
    value.setId(UUID.randomUUID());
    value.setUser(user);
    value.setChallenge(challenge);
    value.setStatus(ChallengeStatus.ACTIVE);
    return value;
  }
}
