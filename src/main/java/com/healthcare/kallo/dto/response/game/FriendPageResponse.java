package com.healthcare.kallo.dto.response.game;

import java.util.List;

public record FriendPageResponse(List<FriendResponse> items, int page, int size, boolean hasNext) {}
