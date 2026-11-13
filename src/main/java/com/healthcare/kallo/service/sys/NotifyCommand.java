package com.healthcare.kallo.service.sys;

import com.healthcare.kallo.model.sys.enums.NotificationType;
import java.util.Map;
import java.util.UUID;

public record NotifyCommand(
    UUID userId,
    NotificationType type,
    Map<String, Object> params,
    Map<String, String> data,
    String dedupeKey) {}
