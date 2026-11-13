package com.healthcare.kallo.event.game;

import java.time.LocalDate;
import java.util.UUID;

public record DailyProgressChangedEvent(UUID userId, LocalDate localDate) {}
