package com.healthcare.kallo.controller.report;

import com.healthcare.kallo.common.ApiResponse;
import com.healthcare.kallo.dto.response.report.DailySummaryResponse;
import com.healthcare.kallo.security.auth.CurrentUser;
import com.healthcare.kallo.service.report.DailySummaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/daily-summaries")
@Tag(name = "Daily Summaries", description = "Aggregated daily nutrition, water, steps, and activity metrics")
public class DailySummaryController {

  private final DailySummaryService dailySummaryService;

  public DailySummaryController(DailySummaryService dailySummaryService) {
    this.dailySummaryService = dailySummaryService;
  }

  @GetMapping("/today")
  @Operation(summary = "Get today's summary", description = "Retrieves today's aggregated nutritional intake, target calories, and activity.")
  public ApiResponse<DailySummaryResponse> getTodaySummary(Authentication authentication) {
    UUID userId = CurrentUser.id(authentication);
    return ApiResponse.success(dailySummaryService.getTodaySummary(userId));
  }

  @GetMapping
  @Operation(summary = "Get daily summary by date", description = "Retrieves aggregated metrics for a specific date.")
  public ApiResponse<DailySummaryResponse> getSummary(
      @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      Authentication authentication) {
    UUID userId = CurrentUser.id(authentication);
    return ApiResponse.success(dailySummaryService.getSummary(userId, date));
  }

  @PostMapping("/recalculate")
  @Operation(summary = "Force recalculate daily summary", description = "Recalculates daily summary for a date (defaults to today if date not provided).")
  public ApiResponse<DailySummaryResponse> recalculate(
      @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      Authentication authentication) {
    UUID userId = CurrentUser.id(authentication);
    LocalDate targetDate = date != null ? date : LocalDate.now();
    dailySummaryService.recalculate(userId, targetDate);
    return ApiResponse.success(dailySummaryService.getSummary(userId, targetDate));
  }
}
