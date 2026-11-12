package com.healthcare.kallo.common.event;

import java.time.Instant;
import java.util.UUID;

public record MealLoggedEvent(UUID userId, String mealType, Instant loggedAt) {}
