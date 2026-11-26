package com.healthcare.kallo.service.nutri;

import com.healthcare.kallo.model.nutri.enums.LogSource;
import java.util.UUID;

/**
 * Optional SPI implemented by module PLAN (MEAL_PLAN) to receive meal log notifications for online
 * preference learning without creating a hard compile-time dependency from NUTRI to PLAN.
 */
public interface FoodPreferenceSyncPort {

  void onFoodLogged(UUID userId, UUID foodId, LogSource source);
}
