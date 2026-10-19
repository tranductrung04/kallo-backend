package com.healthcare.kallo.common;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
  VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Validation failed"),
  EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "Email already exists"),
  INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid credentials"),
  ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "Account disabled"),
  REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Refresh token is invalid"),
  INVALID_CURRENT_PASSWORD(HttpStatus.BAD_REQUEST, "Current password is invalid"),
  PASSWORD_UNCHANGED(HttpStatus.BAD_REQUEST, "New password must differ from current password"),
  PASSWORD_NOT_SET(HttpStatus.BAD_REQUEST, "Password is not set"),
  UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication required"),
  FORBIDDEN(HttpStatus.FORBIDDEN, "Access denied"),
  MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Malformed request"),
  METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed"),
  NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
  UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type"),
  MISSING_PARAMETER(HttpStatus.BAD_REQUEST, "Required parameter is missing"),
  INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "Invalid parameter"),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");

  private final HttpStatus status;
  private final String message;

  ErrorCode(HttpStatus status, String message) {
    this.status = status;
    this.message = message;
  }

  public HttpStatus status() {
    return status;
  }

  public String message() {
    return message;
  }
}
