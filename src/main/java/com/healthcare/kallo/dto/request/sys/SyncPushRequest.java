package com.healthcare.kallo.dto.request.sys;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record SyncPushRequest(@NotEmpty List<SyncOperationRequest> operations) {}
