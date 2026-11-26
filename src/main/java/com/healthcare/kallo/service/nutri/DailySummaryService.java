package com.healthcare.kallo.service.nutri;

import com.healthcare.kallo.dto.response.nutri.DailySummaryResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface DailySummaryService {

  DailySummaryResponse recalculate(UUID userId, LocalDate date);

  DailySummaryResponse getToday(UUID userId);

  DailySummaryResponse getByDate(UUID userId, LocalDate date);

  List<DailySummaryResponse> getRange(UUID userId, LocalDate from, LocalDate to);
}
