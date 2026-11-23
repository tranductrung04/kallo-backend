package com.healthcare.kallo.dto.request.chat;

import jakarta.validation.constraints.NotNull;

public record ChatFeedbackRequest(@NotNull Short feedback) {}
