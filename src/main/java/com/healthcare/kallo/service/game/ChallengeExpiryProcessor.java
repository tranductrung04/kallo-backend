package com.healthcare.kallo.service.game;

import com.healthcare.kallo.model.game.enums.ChallengeStatus;
import com.healthcare.kallo.repository.game.UserChallengeRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChallengeExpiryProcessor {
  private final UserChallengeRepository userChallenges;
  private final ChallengeService challenges;

  public ChallengeExpiryProcessor(UserChallengeRepository userChallenges, ChallengeService challenges) {
    this.userChallenges = userChallenges;
    this.challenges = challenges;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void expire(UUID id) {
    var participation = userChallenges.findByIdForUpdate(id).orElse(null);
    if (participation == null || participation.getStatus() != ChallengeStatus.ACTIVE) return;
    challenges.refresh(participation.getUser().getId(), participation.getChallenge().getEndDate());
    userChallenges.expireIfActive(id, ChallengeStatus.ACTIVE, ChallengeStatus.EXPIRED);
  }
}
