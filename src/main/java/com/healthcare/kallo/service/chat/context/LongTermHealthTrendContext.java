package com.healthcare.kallo.service.chat.context;

import java.math.BigDecimal;

public record LongTermHealthTrendContext(
    BigDecimal startWeightKg,
    BigDecimal endWeightKg,
    BigDecimal weightChangeKg,
    BigDecimal averageCalories,
    BigDecimal averageProteinG,
    BigDecimal averageCarbsG,
    BigDecimal averageFatG) {}
