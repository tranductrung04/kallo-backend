package com.healthcare.kallo.common.event;

import java.time.LocalDate;
import java.util.UUID;

public record DailyProgressChangedEvent(UUID userId, LocalDate localDate) {}
