package com.healthcare.kallo.dto.response.act;

import java.util.List;

public record HealthSyncResponse(
    int received, int created, int updated, int unchanged, List<RejectedRecord> rejected) {
  public record RejectedRecord(int index, String reason) {}
}
