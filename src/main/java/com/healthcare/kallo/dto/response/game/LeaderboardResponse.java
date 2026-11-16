package com.healthcare.kallo.dto.response.game;

import com.healthcare.kallo.model.game.enums.LeaderboardMetric;
import java.time.Instant;
import java.util.List;

public record LeaderboardResponse(
    LeaderboardMetric metric,
    Period period,
    LeaderboardEntryResponse me,
    List<LeaderboardEntryResponse> entries) {
  public record Period(Instant from, Instant to) {}
}
