package com.healthcare.kallo.dto.response.sys;

import java.util.List;
import java.time.Instant;

public record SyncPushResponse(List<SyncOperationResponse> results, Instant serverTime) {}
