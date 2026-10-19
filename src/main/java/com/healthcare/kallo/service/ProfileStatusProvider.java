package com.healthcare.kallo.service;

import com.healthcare.kallo.model.User;

public interface ProfileStatusProvider {
  default boolean isCompleted(User user) {
    return false;
  }
}
