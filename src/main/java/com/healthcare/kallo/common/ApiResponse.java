package com.healthcare.kallo.common;

public record ApiResponse<T>(boolean success, T data, Error error) {

  public static <T> ApiResponse<T> success(T result) {
    return new ApiResponse<>(true, result, null);
  }

  public static <T> ApiResponse<T> errorListDataMessages(String code, String message, T result) {
    return new ApiResponse<>(false, null, new Error(code, message, result));
  }

  public static <T> ApiResponse<T> error(String code, String message) {
    return new ApiResponse<>(false, null, new Error(code, message, null));
  }

  public record Error(String code, String message, Object details) {}
}
