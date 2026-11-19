package com.healthcare.kallo.dto.request.nutri;

import com.healthcare.kallo.model.food.enums.MealType;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CopyMealRequest(
    @NotNull LocalDate fromDate,
    @NotNull LocalDate toDate,
    MealType mealType
) {}
