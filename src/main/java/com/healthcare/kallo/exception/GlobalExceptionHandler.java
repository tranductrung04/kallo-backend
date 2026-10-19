package com.healthcare.kallo.exception;

import com.healthcare.kallo.common.*;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.ConversionNotSupportedException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiResponse<Void>> business(BusinessException e) {
    return ResponseEntity.status(e.getErrorCode().status())
        .body(ApiResponse.error(e.getErrorCode().name(), e.getErrorCode().message()));
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException e,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    Map<String, String> details =
        e.getBindingResult().getFieldErrors().stream()
            .collect(
                Collectors.toMap(
                    f -> validationField(f.getField()),
                    f -> f.getDefaultMessage() == null ? "invalid" : f.getDefaultMessage(),
                    (a, b) -> a));
    return ResponseEntity.badRequest()
        .body(
            ApiResponse.errorListDataMessages(
                ErrorCode.VALIDATION_ERROR.name(), ErrorCode.VALIDATION_ERROR.message(), details));
  }

  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException e,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    return ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.MALFORMED_REQUEST.name(), ErrorCode.MALFORMED_REQUEST.message()));
  }

  @Override
  protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
      HttpRequestMethodNotSupportedException e,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    return new ResponseEntity<>(
        ApiResponse.error(ErrorCode.METHOD_NOT_ALLOWED.name(), ErrorCode.METHOD_NOT_ALLOWED.message()), headers, status);
  }

  @Override
  protected ResponseEntity<Object> handleNoResourceFoundException(
      NoResourceFoundException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    return ResponseEntity.status(status).body(ApiResponse.error(ErrorCode.NOT_FOUND.name(), ErrorCode.NOT_FOUND.message()));
  }

  @Override
  protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(
      org.springframework.web.HttpMediaTypeNotSupportedException e,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    return new ResponseEntity<>(
        ApiResponse.error(ErrorCode.UNSUPPORTED_MEDIA_TYPE.name(), ErrorCode.UNSUPPORTED_MEDIA_TYPE.message()), headers, status);
  }

  @Override
  protected ResponseEntity<Object> handleMissingServletRequestParameter(
      MissingServletRequestParameterException e,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    return ResponseEntity.badRequest()
        .body(ApiResponse.error(ErrorCode.MISSING_PARAMETER.name(), ErrorCode.MISSING_PARAMETER.message()));
  }

  @Override
  protected ResponseEntity<Object> handleTypeMismatch(
      TypeMismatchException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    return ResponseEntity.badRequest()
        .body(ApiResponse.error(ErrorCode.INVALID_PARAMETER.name(), ErrorCode.INVALID_PARAMETER.message()));
  }

  @Override
  protected ResponseEntity<Object> handleConversionNotSupported(
      ConversionNotSupportedException e,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    return ResponseEntity.internalServerError()
        .body(
            ApiResponse.error(ErrorCode.INTERNAL_ERROR.name(), ErrorCode.INTERNAL_ERROR.message()));
  }

  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception e, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    if (status.is4xxClientError()) {
      return ResponseEntity.status(status)
          .body(ApiResponse.error("REQUEST_ERROR", "Request could not be processed"));
    }
    return ResponseEntity.status(status)
        .body(
            ApiResponse.error(ErrorCode.INTERNAL_ERROR.name(), ErrorCode.INTERNAL_ERROR.message()));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ApiResponse<Void>> conflict(DataIntegrityViolationException e) {
    log.warn("Data integrity violation while processing request");
    if (rootMessage(e).contains("uq_users_email")) {
      return ResponseEntity.status(409)
          .body(ApiResponse.error("EMAIL_ALREADY_EXISTS", "Email already exists"));
    }
    return ResponseEntity.internalServerError()
        .body(
            ApiResponse.error(ErrorCode.INTERNAL_ERROR.name(), ErrorCode.INTERNAL_ERROR.message()));
  }

  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<ApiResponse<Void>> authentication(AuthenticationException e) {
    return ResponseEntity.status(401)
        .body(ApiResponse.error(ErrorCode.UNAUTHORIZED.name(), ErrorCode.UNAUTHORIZED.message()));
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiResponse<Void>> accessDenied(AccessDeniedException e) {
    return ResponseEntity.status(403).body(ApiResponse.error(ErrorCode.FORBIDDEN.name(), ErrorCode.FORBIDDEN.message()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> unexpected(Exception e) {
    log.error("Unhandled request error", e);
    return ResponseEntity.internalServerError()
        .body(
            ApiResponse.error(ErrorCode.INTERNAL_ERROR.name(), ErrorCode.INTERNAL_ERROR.message()));
  }

  private String rootMessage(Throwable error) {
    Throwable current = error;
    while (current.getCause() != null) current = current.getCause();
    return current.getMessage() == null ? "" : current.getMessage();
  }

  private String validationField(String field) {
    return field.equals("passwordWithinBcryptLimit") ? "password" : field;
  }
}
