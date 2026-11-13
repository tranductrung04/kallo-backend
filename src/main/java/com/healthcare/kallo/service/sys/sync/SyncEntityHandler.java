package com.healthcare.kallo.service.sys.sync;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SyncEntityHandler {
  SyncEntityType type();

  SyncApplyResult apply(UUID userId, SyncOperation operation);

  List<SyncChange> changesSince(
      UUID userId, long sinceSeq, Instant settledBefore, int limitPlusOne);

  default int cleanupTombstonesBefore(Instant cutoff) {
    return 0;
  }
}
