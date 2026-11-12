package com.healthcare.kallo.event.sys;

import java.time.Instant;
import java.util.UUID;

public record MealLoggedEvent(UUID userId, String mealType, Instant loggedAt) {}
