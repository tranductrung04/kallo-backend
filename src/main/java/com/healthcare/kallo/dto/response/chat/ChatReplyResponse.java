package com.healthcare.kallo.dto.response.chat;

import java.util.UUID;

public record ChatReplyResponse(UUID conversationId, ChatMessageResponse userMessage,
    ChatMessageResponse assistantMessage) {}
