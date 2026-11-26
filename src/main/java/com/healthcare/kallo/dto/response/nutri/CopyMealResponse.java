package com.healthcare.kallo.dto.response.nutri;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CopyMealResponse(
    LocalDate sourceDate,
    LocalDate targetDate,
    int copiedCount,
    List<FoodLogResponse> copiedLogs,
    List<SkippedFoodItem> skippedFoods) {

  public record SkippedFoodItem(UUID foodId, String reason) {}
}
