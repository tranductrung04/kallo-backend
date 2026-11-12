package com.healthcare.kallo.service.sys;

public record PushResult(String token, Status status) {
  public enum Status {
    SENT,
    INVALID_TOKEN,
    RETRYABLE_FAILURE,
    FAILURE
  }
}
