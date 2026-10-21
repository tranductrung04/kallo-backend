package com.healthcare.kallo.service;

import com.healthcare.kallo.model.User;

public interface EmailService {
  void sendVerificationCode(User user, String code);
  void sendPasswordResetCode(User user, String code);
}
