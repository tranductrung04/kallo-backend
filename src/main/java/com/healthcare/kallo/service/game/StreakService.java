package com.healthcare.kallo.service.game;

import com.healthcare.kallo.dto.response.game.StreakResponse;
import java.time.LocalDate;
import java.util.UUID;

public interface StreakService {
  void recompute(UUID userId, LocalDate changedDate);

  StreakResponse get(UUID userId);
}
