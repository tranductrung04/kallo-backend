package com.healthcare.kallo.dto.response.nutri;

import java.util.List;

public record RecognizeFoodResponse(
    String imageUrl,
    List<RecognizeFoodItemResponse> predictions
) {}
