package com.healthcare.kallo.dto.response.nutri;

import java.time.LocalDate;
import java.util.List;

public record DailyWaterResponse(
    LocalDate date,
    int totalWaterMl,
    int targetWaterMl,
    List<WaterLogResponse> logs
) {}
