package com.healthcare.kallo.service.game;

import com.healthcare.kallo.dto.response.game.LeaderboardResponse;
import com.healthcare.kallo.model.game.enums.LeaderboardMetric;
import java.util.UUID;

public interface LeaderboardService {
  LeaderboardResponse get(UUID userId, LeaderboardMetric metric);

  long valueOf(UUID userId, LeaderboardMetric metric);
}
