package com.healthcare.kallo.service.sys;

public interface NotificationService {
  NotifyOutcome notify(NotifyCommand command);
}
