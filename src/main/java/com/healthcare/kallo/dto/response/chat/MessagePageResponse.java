package com.healthcare.kallo.dto.response.chat;

import java.util.List;

public record MessagePageResponse(List<ChatMessageResponse> items, String nextCursor, boolean hasMore) {}
