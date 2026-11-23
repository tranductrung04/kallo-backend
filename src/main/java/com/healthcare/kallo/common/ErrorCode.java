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
  GOOGLE_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Google token is invalid"),
  GOOGLE_ACCOUNT_CONFLICT(HttpStatus.CONFLICT, "Google account is already linked to another account"),
  GOOGLE_PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Google authentication is temporarily unavailable"),
  VERIFICATION_CODE_INVALID(HttpStatus.BAD_REQUEST, "Verification code is invalid"),
  RESET_CODE_INVALID(HttpStatus.BAD_REQUEST, "Reset code is invalid"),
  EMAIL_ALREADY_VERIFIED(HttpStatus.CONFLICT, "Email is already verified"),
  TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "Too many requests"),
  EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "Email is not verified"),
  UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication required"),
  FORBIDDEN(HttpStatus.FORBIDDEN, "Access denied"),
  MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Malformed request"),
  METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed"),
  NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
  UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type"),
  MISSING_PARAMETER(HttpStatus.BAD_REQUEST, "Required parameter is missing"),
  INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "Invalid parameter"),
  CHALLENGE_LOCKED(HttpStatus.CONFLICT, "Challenge metric and target cannot change after participation"),
  CANNOT_FRIEND_SELF(HttpStatus.BAD_REQUEST, "Cannot friend yourself"),
  ALREADY_FRIENDS(HttpStatus.CONFLICT, "Users are already friends"),
  FRIEND_REQUEST_PENDING(HttpStatus.CONFLICT, "Friend request is pending"),
  FRIEND_REQUEST_COOLDOWN(HttpStatus.CONFLICT, "Friend request is on cooldown"),
  FRIEND_LIMIT_REACHED(HttpStatus.CONFLICT, "Friend limit reached"),
  CHALLENGE_ALREADY_JOINED(HttpStatus.CONFLICT, "Challenge already joined"),
  CHALLENGE_NOT_JOINABLE(HttpStatus.CONFLICT, "Challenge is not joinable"),
  INVALID_FILE(HttpStatus.BAD_REQUEST, "Invalid file"),
  PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "Payload is too large"),
  ACTIVITY_TYPE_UNKNOWN(HttpStatus.BAD_REQUEST, "Activity type is unknown"),
  WEIGHT_REQUIRED(HttpStatus.UNPROCESSABLE_ENTITY, "Weight is required"),
  RECORD_DELETED(HttpStatus.CONFLICT, "Record has been deleted"),
  INVALID_TIME_RANGE(HttpStatus.BAD_REQUEST, "Invalid time range"),
  SYNC_BATCH_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "Sync batch is too large"),
  TIMESTAMP_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "Timestamp is out of range"),
  NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Notification not found"),
  DEVICE_NOT_FOUND(HttpStatus.NOT_FOUND, "Device not found"),
  FULL_RESYNC_REQUIRED(HttpStatus.CONFLICT, "Full resynchronization is required"),
  INVALID_DELETE_CONFIRMATION(HttpStatus.UNAUTHORIZED, "Account deletion confirmation is invalid"),
  PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "Profile not found"),
  INVALID_BIRTH_DATE(HttpStatus.BAD_REQUEST, "Birth date is invalid"),
  INVALID_MACRO_RATIO(HttpStatus.BAD_REQUEST, "Total macro percentage must equal 100%"),
  FOOD_NOT_FOUND(HttpStatus.NOT_FOUND, "Food item not found"),
  CANNOT_MODIFY_SYSTEM_FOOD(HttpStatus.FORBIDDEN, "Cannot modify or delete system food"),
  INVALID_BARCODE(HttpStatus.BAD_REQUEST, "Invalid barcode format"),
  BARCODE_NOT_FOUND(HttpStatus.NOT_FOUND, "Barcode not found in local database or Open Food Facts"),
  NON_FOOD_DETECTED(HttpStatus.BAD_REQUEST, "Detected object is not a food item"),
  FOOD_LOG_NOT_FOUND(HttpStatus.NOT_FOUND, "Food log not found"),
  WATER_LOG_NOT_FOUND(HttpStatus.NOT_FOUND, "Water log not found"),
  DAILY_SUMMARY_NOT_FOUND(HttpStatus.NOT_FOUND, "Daily summary not found"),
  AI_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AI recognition service is temporarily unavailable"),
  MEAL_PLAN_NOT_FOUND(HttpStatus.NOT_FOUND, "Meal plan not found"),
  MEAL_PLAN_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "Meal plan item not found"),
  INSUFFICIENT_FOOD_DATA(HttpStatus.BAD_REQUEST, "Insufficient suitable food items to generate meal plan"),
  NO_SUITABLE_SWAP_FOOD(HttpStatus.BAD_REQUEST, "No suitable alternative food found for swap"),
  MEAL_PLAN_NOT_SAVED(HttpStatus.BAD_REQUEST, "Meal plan must be saved before applying"),
  MEAL_PLAN_ALREADY_APPLIED(HttpStatus.CONFLICT, "Meal plan has already been applied"),
  MEAL_LOG_ALREADY_EXISTS(HttpStatus.CONFLICT, "Meal log already exists for this date and meal type"),
  FOOD_IN_USE(HttpStatus.CONFLICT, "Food item is in use by meal plans or food logs"),
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
