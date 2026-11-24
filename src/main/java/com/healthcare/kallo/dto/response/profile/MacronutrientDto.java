package com.healthcare.kallo.dto.response.profile;

import java.math.BigDecimal;

public record MacronutrientDto(
    BigDecimal proteinGrams,
    BigDecimal fatGrams,
    BigDecimal carbGrams
) {}
