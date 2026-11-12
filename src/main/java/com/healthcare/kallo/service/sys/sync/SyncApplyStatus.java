package com.healthcare.kallo.service.sys.sync;

public enum SyncApplyStatus {
  APPLIED,
  UNCHANGED,
  CONFLICT_SERVER_WINS,
  REJECTED
}
