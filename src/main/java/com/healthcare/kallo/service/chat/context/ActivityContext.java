package com.healthcare.kallo.service.chat.context;

import java.math.BigDecimal;
import java.util.List;

public record ActivityContext(
    Integer steps,
    BigDecimal activeCalories,
    Integer exerciseMinutes,
    List<Activity> activities,
    Integer waterMl) {
  public ActivityContext {
    activities = activities == null ? List.of() : List.copyOf(activities);
  }

  public record Activity(String type, Integer durationMinutes, BigDecimal calories) {}
}
