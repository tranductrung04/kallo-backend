package com.healthcare.kallo.service.chat.context;

import java.math.BigDecimal;

public record NutritionGoalsContext(
    BigDecimal dailyCalories,
    BigDecimal tdee,
    BigDecimal proteinG,
    BigDecimal carbsG,
    BigDecimal fatG,
    BigDecimal waterMl) {}
