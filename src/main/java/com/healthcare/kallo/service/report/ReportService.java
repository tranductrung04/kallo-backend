package com.healthcare.kallo.service.report;

import com.healthcare.kallo.dto.response.report.*;
import java.time.LocalDate;
import java.util.UUID;

public interface ReportService {

  DashboardReportResponse getDashboard(UUID userId, LocalDate date);

  CaloriesReportResponse getCaloriesReport(UUID userId, String period, LocalDate endDate);

  MacrosReportResponse getMacrosReport(UUID userId, String period, LocalDate endDate);

  WeightReportResponse getWeightReport(UUID userId, String period, LocalDate endDate);

  WeightForecastResponse getWeightForecast(UUID userId);

  PeriodSummaryReportResponse getPeriodSummary(UUID userId, LocalDate startDate, LocalDate endDate);
}
