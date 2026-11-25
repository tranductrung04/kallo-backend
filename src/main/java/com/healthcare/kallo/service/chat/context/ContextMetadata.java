package com.healthcare.kallo.service.chat.context;

import java.time.Instant;

public record ContextMetadata(String schemaVersion, Instant asOf, String timezone) {}
