package com.healthcare.kallo.dto.response.chat;

import java.util.List;

public record ConversationPageResponse(List<ConversationResponse> items, int page, int size,
    long totalElements, int totalPages) {}
