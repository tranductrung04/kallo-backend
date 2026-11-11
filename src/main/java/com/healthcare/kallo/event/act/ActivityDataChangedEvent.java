package com.healthcare.kallo.event.act;

import java.time.LocalDate;
import java.util.UUID;

public record ActivityDataChangedEvent(UUID userId, LocalDate localDate) {}
