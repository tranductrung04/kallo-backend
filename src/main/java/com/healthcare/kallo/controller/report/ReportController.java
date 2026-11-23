package com.healthcare.kallo.controller.report;

import com.healthcare.kallo.common.ApiResponse;
import com.healthcare.kallo.dto.response.report.*;
import com.healthcare.kallo.security.auth.CurrentUser;
import com.healthcare.kallo.service.report.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports")
@Tag(name = "Reports", description = "Aggregated nutrition, calories, macros, weight history, and weight trend forecasting")
public class ReportController {

  private final ReportService reportService;

  public ReportController(ReportService reportService) {
    this.reportService = reportService;
  }

  @GetMapping("/dashboard")
  @Operation(summary = "Get daily progress dashboard", description = "Retrieves today's or specified date's aggregated progress for nutrition, water, and activity.")
  public ApiResponse<DashboardReportResponse> getDashboard(
      @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      Authentication authentication) {
    UUID userId = CurrentUser.id(authentication);
    return ApiResponse.success(reportService.getDashboard(userId, date));
  }

  @GetMapping("/calories")
  @Operation(summary = "Get calories in vs out time series", description = "Retrieves daily calories in, burned, and net time series with scaffolding.")
  public ApiResponse<CaloriesReportResponse> getCaloriesReport(
      @RequestParam(name = "period", defaultValue = "WEEK") String period,
      @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      Authentication authentication) {
    UUID userId = CurrentUser.id(authentication);
    return ApiResponse.success(reportService.getCaloriesReport(userId, period, date));
  }

  @GetMapping("/macros")
  @Operation(summary = "Get macro distribution report", description = "Retrieves daily protein, carbs, fat intake and overall macronutrient breakdown.")
  public ApiResponse<MacrosReportResponse> getMacrosReport(
      @RequestParam(name = "period", defaultValue = "WEEK") String period,
      @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      Authentication authentication) {
    UUID userId = CurrentUser.id(authentication);
    return ApiResponse.success(reportService.getMacrosReport(userId, period, date));
  }

  @GetMapping("/weight")
  @Operation(summary = "Get weight progress chart", description = "Retrieves logged weight over time compared against user's target weight.")
  public ApiResponse<WeightReportResponse> getWeightReport(
      @RequestParam(name = "period", defaultValue = "MONTH") String period,
      @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      Authentication authentication) {
    UUID userId = CurrentUser.id(authentication);
    return ApiResponse.success(reportService.getWeightReport(userId, period, date));
  }

  @GetMapping("/weight-forecast")
  @Operation(summary = "Get 30-day weight forecast (EMA)", description = "Calculates projected weight change over 30 days and estimated goal date using EMA.")
  public ApiResponse<WeightForecastResponse> getWeightForecast(Authentication authentication) {
    UUID userId = CurrentUser.id(authentication);
    return ApiResponse.success(reportService.getWeightForecast(userId));
  }

  @GetMapping("/summary")
  @Operation(summary = "Get period summary report", description = "Retrieves comprehensive statistics for a custom period for PDF or image export.")
  public ApiResponse<PeriodSummaryReportResponse> getPeriodSummary(
      @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
      @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
      Authentication authentication) {
    UUID userId = CurrentUser.id(authentication);
    return ApiResponse.success(reportService.getPeriodSummary(userId, startDate, endDate));
  }
}
