package com.healthcare.kallo.service.auth;

import com.healthcare.kallo.model.auth.User;

public interface EmailService {
  void sendVerificationCode(User user, String code);
  void sendPasswordResetCode(User user, String code);
}
