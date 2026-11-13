package com.healthcare.kallo.service.game;

import com.healthcare.kallo.dto.response.game.StreakResponse;
import java.util.UUID;

public interface StreakService {
  void recompute(UUID userId);

  StreakResponse get(UUID userId);
}
