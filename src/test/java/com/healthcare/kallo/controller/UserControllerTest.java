package com.healthcare.kallo.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.healthcare.kallo.dto.response.UserProfileResponse;
import com.healthcare.kallo.exception.GlobalExceptionHandler;
import com.healthcare.kallo.service.UserService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(value = UserController.class, properties = "spring.profiles.active=test")
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {
  @Autowired MockMvc mockMvc;
  @MockitoBean UserService userService;

  @Test
  void invalidTimezoneReturnsValidationDetails() throws Exception {
    mockMvc
        .perform(
            put("/api/users/me")
                .with(
                    SecurityMockMvcRequestPostProcessors.authentication(
                        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                            "user", null)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"User\",\"timezone\":\"not/a-timezone\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.details.timezone").exists());
  }

  @Test
  void missingAvatarReturnsInvalidFile() throws Exception {
    mockMvc
        .perform(
            multipart("/api/users/me/avatar")
                .with(
                    SecurityMockMvcRequestPostProcessors.authentication(
                        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                            "user", null))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_FILE"));
  }

  @Test
  void nonMultipartAvatarRequestReturnsInvalidFile() throws Exception {
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                    "/api/users/me/avatar")
                .with(
                    SecurityMockMvcRequestPostProcessors.authentication(
                        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                            "user", null)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_FILE"));
  }

  @Test
  void missingAvatarMultipartRequestReturnsInvalidFile() throws Exception {
    mockMvc
        .perform(
            multipart("/api/users/me/avatar")
                .with(
                    SecurityMockMvcRequestPostProcessors.authentication(
                        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                            "user", null))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_FILE"));
  }

  @Test
  void multipartExceptionReturnsInvalidFile() {
    var response = new GlobalExceptionHandler().multipart(new MultipartException("not multipart"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().error().code()).isEqualTo("INVALID_FILE");
  }

  @Test
  void maxUploadSizeExceptionReturnsPayloadTooLarge() {
    var handler = new TestableGlobalExceptionHandler();
    var response =
        handler.handleInternal(
            new MaxUploadSizeExceededException(2 * 1024 * 1024L),
            null,
            new HttpHeaders(),
            HttpStatus.PAYLOAD_TOO_LARGE,
            new org.springframework.web.context.request.ServletWebRequest(new MockHttpServletRequest()));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
    assertThat(((com.healthcare.kallo.common.ApiResponse<?>) response.getBody()).error().code())
        .isEqualTo("PAYLOAD_TOO_LARGE");
  }

  private static final class TestableGlobalExceptionHandler extends GlobalExceptionHandler {
    ResponseEntity<Object> handleInternal(
        Exception exception,
        Object body,
        HttpHeaders headers,
        HttpStatus status,
        org.springframework.web.context.request.WebRequest request) {
      return super.handleExceptionInternal(exception, body, headers, status, request);
    }

    ResponseEntity<Object> handleMediaTypeNotSupported(
        org.springframework.web.HttpMediaTypeNotSupportedException exception,
        HttpHeaders headers,
        HttpStatus status,
        org.springframework.web.context.request.WebRequest request) {
      return super.handleHttpMediaTypeNotSupported(exception, headers, status, request);
    }
  }
}
