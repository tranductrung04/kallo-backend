package com.healthcare.kallo.service.sys.sync;

import java.time.Instant;
import java.util.Map;

public record SyncApplyResult(
    SyncApplyStatus status,
    String reason,
    Map<String, Object> serverData,
    Instant serverUpdatedAt) {
  public static SyncApplyResult applied() {
    return new SyncApplyResult(SyncApplyStatus.APPLIED, null, null, null);
  }

  public static SyncApplyResult unchanged() {
    return new SyncApplyResult(SyncApplyStatus.UNCHANGED, null, null, null);
  }

  public static SyncApplyResult conflictServerWins(
      Map<String, Object> serverData, Instant serverUpdatedAt) {
    return new SyncApplyResult(
        SyncApplyStatus.CONFLICT_SERVER_WINS, null, serverData, serverUpdatedAt);
  }

  public static SyncApplyResult rejected(String reason) {
    return new SyncApplyResult(SyncApplyStatus.REJECTED, reason, null, null);
  }
}
