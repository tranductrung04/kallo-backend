package com.healthcare.kallo.controller.nutri;

import com.healthcare.kallo.common.ApiResponse;
import com.healthcare.kallo.dto.response.nutri.DailySummaryResponse;
import com.healthcare.kallo.security.auth.CurrentUser;
import com.healthcare.kallo.service.nutri.DailySummaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/daily-summaries")
@Tag(name = "Daily summaries", description = "Calorie, macro, water and activity daily rollups")
public class DailySummaryController {

  private final DailySummaryService dailySummaryService;

  public DailySummaryController(DailySummaryService dailySummaryService) {
    this.dailySummaryService = dailySummaryService;
  }

  @GetMapping("/today")
  @Operation(summary = "Get today's daily nutrition and activity summary")
  public ApiResponse<DailySummaryResponse> getToday(Authentication authentication) {
    return ApiResponse.success(dailySummaryService.getToday(CurrentUser.id(authentication)));
  }

  @GetMapping
  @Operation(summary = "Get daily summary for a specific date")
  public ApiResponse<DailySummaryResponse> getByDate(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      Authentication authentication) {
    return ApiResponse.success(
        dailySummaryService.getByDate(CurrentUser.id(authentication), date));
  }

  @GetMapping("/range")
  @Operation(summary = "Get daily summaries over a date range (max 90 days)")
  public ApiResponse<List<DailySummaryResponse>> getRange(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      Authentication authentication) {
    return ApiResponse.success(
        dailySummaryService.getRange(CurrentUser.id(authentication), from, to));
  }

  @PostMapping("/recalculate")
  @Operation(summary = "Force recalculate daily summary for a date (defaults to today)")
  public ApiResponse<DailySummaryResponse> recalculate(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      Authentication authentication) {
    UUID userId = CurrentUser.id(authentication);
    LocalDate targetDate = date != null ? date : dailySummaryService.getToday(userId).date();
    return ApiResponse.success(dailySummaryService.recalculate(userId, targetDate));
  }
}
