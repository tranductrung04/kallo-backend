package com.healthcare.kallo.service.act;

import com.healthcare.kallo.dto.response.act.DailyActivityStats;
import java.time.LocalDate;
import java.util.UUID;

public interface ActivityDailyAggregator {
  DailyActivityStats aggregate(UUID userId, LocalDate date);
}
