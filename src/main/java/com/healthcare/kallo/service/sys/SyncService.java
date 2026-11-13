package com.healthcare.kallo.service.sys;

import com.healthcare.kallo.dto.response.sys.SyncPullResponse;
import com.healthcare.kallo.dto.response.sys.SyncPushResponse;
import com.healthcare.kallo.service.sys.sync.SyncOperation;
import java.util.List;
import java.util.UUID;

public interface SyncService {
  SyncPushResponse push(UUID userId, List<SyncOperation> operations);

  SyncPullResponse pull(UUID userId, String cursor, Integer limit);
}
