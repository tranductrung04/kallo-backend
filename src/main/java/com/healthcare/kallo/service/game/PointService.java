package com.healthcare.kallo.service.game;

import com.healthcare.kallo.model.game.enums.PointSource;
import java.util.UUID;

public interface PointService {
  boolean award(UUID userId, PointSource source, UUID sourceId, int points);
}
