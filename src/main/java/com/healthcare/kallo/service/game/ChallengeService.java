package com.healthcare.kallo.service.game;

import com.healthcare.kallo.dto.response.game.ChallengeResponse;
import java.util.List;
import java.util.UUID;

public interface ChallengeService {
  List<ChallengeResponse> list(UUID userId, String status);
  ChallengeResponse get(UUID userId, UUID challengeId);
  ChallengeResponse join(UUID userId, UUID challengeId);
  void refresh(UUID userId, java.time.LocalDate localDate);
}
