package com.healthcare.kallo.dto.response.sys;

import com.healthcare.kallo.service.sys.sync.SyncChange;
import java.time.Instant;
import java.util.List;

public record SyncPullResponse(
    List<SyncChange> changes, String nextCursor, boolean hasMore, Instant serverTime) {}
