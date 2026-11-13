package com.healthcare.kallo.dto.response.sys;

import java.util.List;

public record NotificationPageResponse(
    List<NotificationResponse> items, String nextCursor, long unreadCount) {}
