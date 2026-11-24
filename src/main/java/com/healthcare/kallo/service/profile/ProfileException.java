package com.healthcare.kallo.service.profile;

import com.healthcare.kallo.model.profile.ProfileErrorCode;

public class ProfileException extends RuntimeException {
  private final ProfileErrorCode errorCode;

  public ProfileException(ProfileErrorCode errorCode) {
    super(errorCode.getMessage());
    this.errorCode = errorCode;
  }

  public ProfileErrorCode getErrorCode() {
    return errorCode;
  }
}
