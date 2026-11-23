package com.healthcare.kallo.dto.request.plan;

import java.util.UUID;

public record SwapFoodItemRequest(
    UUID alternativeFoodId
) {}
