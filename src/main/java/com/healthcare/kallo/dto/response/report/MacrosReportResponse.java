package com.healthcare.kallo.dto.response.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record MacrosReportResponse(
    String period,
    LocalDate startDate,
    LocalDate endDate,
    BigDecimal avgProteinG,
    BigDecimal avgCarbsG,
    BigDecimal avgFatG,
    BigDecimal proteinPct,
    BigDecimal carbsPct,
    BigDecimal fatPct,
    List<Item> timeSeries
) {
  public record Item(
      LocalDate date,
      BigDecimal proteinG,
      BigDecimal carbsG,
      BigDecimal fatG,
      boolean hasData
  ) {}
}
