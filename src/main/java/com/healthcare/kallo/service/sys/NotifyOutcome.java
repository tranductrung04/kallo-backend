package com.healthcare.kallo.service.sys;

public enum NotifyOutcome {
  SENT,
  SUPPRESSED_DISABLED,
  SUPPRESSED_PUSH_ONLY,
  SUPPRESSED_DND_REMINDER,
  DUPLICATE,
  CAP_REACHED
}
