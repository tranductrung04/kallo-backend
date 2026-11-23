package com.healthcare.kallo.dto.response.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record WeightReportResponse(
    String period,
    BigDecimal startWeightKg,
    BigDecimal currentWeightKg,
    BigDecimal targetWeightKg,
    BigDecimal totalChangeKg,
    List<Item> timeSeries
) {
  public record Item(
      LocalDate date,
      BigDecimal weightKg,
      BigDecimal targetWeightKg
  ) {}
}
