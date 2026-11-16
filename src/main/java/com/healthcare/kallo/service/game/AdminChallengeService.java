package com.healthcare.kallo.service.game;

import com.healthcare.kallo.dto.request.game.CreateChallengeRequest;
import com.healthcare.kallo.dto.request.game.UpdateChallengeRequest;
import com.healthcare.kallo.dto.response.game.AdminChallengeResponse;
import java.util.List;
import java.util.UUID;

public interface AdminChallengeService {
  List<AdminChallengeResponse> list();
  AdminChallengeResponse create(CreateChallengeRequest request);
  AdminChallengeResponse update(UUID challengeId, UpdateChallengeRequest request);
}
