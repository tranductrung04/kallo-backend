package com.healthcare.kallo.dto.response.act;

import java.math.BigDecimal;

public record ActivityTypeResponse(
    String code, String nameVi, String category, BigDecimal metLight,
    BigDecimal metModerate, BigDecimal metVigorous, boolean hasDistance, int sortOrder) {}
