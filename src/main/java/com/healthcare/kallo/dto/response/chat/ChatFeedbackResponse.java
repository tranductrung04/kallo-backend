package com.healthcare.kallo.dto.response.chat;

import java.util.UUID;

public record ChatFeedbackResponse(UUID messageId, short feedback) {}
