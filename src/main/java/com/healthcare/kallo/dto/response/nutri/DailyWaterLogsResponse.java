package com.healthcare.kallo.dto.response.nutri;

import java.time.LocalDate;
import java.util.List;

public record DailyWaterLogsResponse(
    LocalDate date,
    int totalWaterMl,
    Integer targetWaterMl,
    List<WaterLogResponse> logs) {}
