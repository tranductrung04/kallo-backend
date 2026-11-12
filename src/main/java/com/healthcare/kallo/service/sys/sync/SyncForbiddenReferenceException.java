package com.healthcare.kallo.service.sys.sync;

public class SyncForbiddenReferenceException extends RuntimeException {
  public SyncForbiddenReferenceException() {}

  public SyncForbiddenReferenceException(String message) {
    super(message);
  }
}
