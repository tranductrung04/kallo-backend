package com.healthcare.kallo.service.game;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only boundary from GAME to modules owning daily health data.
 *
 * <p>{@code mealsLogged} means number of different meal slots with at least one log, not number
 * of log rows. A range is inclusive at both ends and contains at most one row per local date.
 * Missing dates are omitted from {@link #findRange(UUID, LocalDate, LocalDate)} and represented by
 * {@link Optional#empty()} from {@link #find(UUID, LocalDate)}. A null calorie goal means the user
 * has not configured a calorie target.
 *
 * <p>Callers use a maximum range of {@code app.game.streak.window-days} (400 by default). Provider
 * implementations should reject ranges longer than that configured window rather than silently
 * returning partial data. All dates are already local dates in the user's timezone.
 */
public interface DailyProgressProvider {
  Optional<DailyProgress> find(UUID userId, LocalDate localDate);

  List<DailyProgress> findRange(UUID userId, LocalDate fromInclusive, LocalDate toInclusive);

  long sumSteps(UUID userId, LocalDate fromInclusive, LocalDate toInclusive);
}
