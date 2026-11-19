package com.healthcare.kallo.service.report;

import com.healthcare.kallo.dto.response.report.DailySummaryResponse;
import java.time.LocalDate;
import java.util.UUID;

public interface DailySummaryService {

  void recalculate(UUID userId, LocalDate date);

  DailySummaryResponse getSummary(UUID userId, LocalDate date);

  DailySummaryResponse getTodaySummary(UUID userId);
}
