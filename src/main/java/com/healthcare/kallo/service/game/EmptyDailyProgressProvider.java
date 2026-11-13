package com.healthcare.kallo.service.game;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class EmptyDailyProgressProvider implements DailyProgressProvider {
  @Override
  public Optional<DailyProgress> find(UUID userId, LocalDate localDate) {
    return Optional.empty();
  }

  @Override
  public List<DailyProgress> findRange(
      UUID userId, LocalDate fromInclusive, LocalDate toInclusive) {
    return List.of();
  }

  @Override
  public long sumSteps(UUID userId, LocalDate fromInclusive, LocalDate toInclusive) {
    return 0;
  }
}
