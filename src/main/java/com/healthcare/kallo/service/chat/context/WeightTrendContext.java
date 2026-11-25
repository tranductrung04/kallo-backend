package com.healthcare.kallo.service.chat.context;

import java.math.BigDecimal;

public record WeightTrendContext(
    BigDecimal currentWeightKg,
    BigDecimal weight7DaysAgoKg,
    BigDecimal weight30DaysAgoKg,
    BigDecimal weeklyChangeKg) {}
