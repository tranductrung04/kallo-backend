package com.healthcare.kallo.service.auth;

import com.healthcare.kallo.model.auth.User;

public interface ProfileStatusProvider {
  default boolean isCompleted(User user) {
    return false;
  }
}
