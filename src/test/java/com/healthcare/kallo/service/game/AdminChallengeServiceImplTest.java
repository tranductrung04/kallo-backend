package com.healthcare.kallo.service.game;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.dto.request.game.CreateChallengeRequest;
import com.healthcare.kallo.dto.request.game.UpdateChallengeRequest;
import com.healthcare.kallo.model.game.Challenge;
import com.healthcare.kallo.model.game.enums.ChallengeMetric;
import com.healthcare.kallo.repository.game.BadgeRepository;
import com.healthcare.kallo.repository.game.ChallengeRepository;
import com.healthcare.kallo.repository.game.UserChallengeRepository;
import com.healthcare.kallo.service.game.impl.AdminChallengeServiceImpl;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AdminChallengeServiceImplTest {
  @Test
  void rejectsMetricChangeAfterParticipationWithConflict() {
    ChallengeRepository challenges = Mockito.mock(ChallengeRepository.class);
    BadgeRepository badges = Mockito.mock(BadgeRepository.class);
    UserChallengeRepository participations = Mockito.mock(UserChallengeRepository.class);
    UUID challengeId = UUID.randomUUID();
    Challenge challenge = new Challenge();
    challenge.setId(challengeId);
    challenge.setMetric(ChallengeMetric.LOG_DAYS);
    challenge.setTargetValue(3);
    when(challenges.findById(challengeId)).thenReturn(java.util.Optional.of(challenge));
    when(participations.existsByChallengeId(challengeId)).thenReturn(true);

    UpdateChallengeRequest request = new UpdateChallengeRequest(
        "Challenge", "Description", ChallengeMetric.STEPS_TOTAL, 3,
        LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), 10, null, true);

    assertThatThrownBy(() -> new AdminChallengeServiceImpl(challenges, badges, participations)
        .update(challengeId, request))
        .isInstanceOfSatisfying(BusinessException.class,
            error -> org.assertj.core.api.Assertions.assertThat(error.getErrorCode())
                .isEqualTo(ErrorCode.CHALLENGE_LOCKED));
  }

  @Test
  void rejectsEndDateBeforeStartDate() {
    ChallengeRepository challenges = Mockito.mock(ChallengeRepository.class);
    BadgeRepository badges = Mockito.mock(BadgeRepository.class);
    UserChallengeRepository participations = Mockito.mock(UserChallengeRepository.class);
    CreateChallengeRequest request = new CreateChallengeRequest(
        "Challenge", "Description", ChallengeMetric.LOG_DAYS, 3,
        LocalDate.of(2026, 11, 30), LocalDate.of(2026, 11, 1), 10, null, true);

    assertThatThrownBy(() -> new AdminChallengeServiceImpl(challenges, badges, participations)
        .create(request))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_PARAMETER);
  }
}
