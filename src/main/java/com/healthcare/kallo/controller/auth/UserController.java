package com.healthcare.kallo.controller.auth;

import com.healthcare.kallo.common.ApiResponse;
import com.healthcare.kallo.dto.request.auth.DeleteAccountRequest;
import com.healthcare.kallo.dto.request.auth.UpdateUserRequest;
import com.healthcare.kallo.dto.response.auth.AvatarResponse;
import com.healthcare.kallo.dto.response.auth.UserProfileResponse;
import com.healthcare.kallo.security.auth.CurrentUser;
import com.healthcare.kallo.service.auth.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/users/me")
public class UserController {
  private final UserService users;

  public UserController(UserService users) {
    this.users = users;
  }

  @GetMapping
  public ApiResponse<UserProfileResponse> getMe(Authentication authentication) {
    return ApiResponse.success(users.getMe(CurrentUser.id(authentication)));
  }

  @PutMapping
  public ApiResponse<UserProfileResponse> updateMe(
      @Valid @RequestBody UpdateUserRequest request, Authentication authentication) {
    return ApiResponse.success(users.updateMe(CurrentUser.id(authentication), request));
  }

  @PostMapping("/avatar")
  public ApiResponse<AvatarResponse> updateAvatar(
      @RequestPart("file") MultipartFile file, Authentication authentication) {
    return ApiResponse.success(users.updateAvatar(CurrentUser.id(authentication), file));
  }

  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteAccount(
      @Valid @RequestBody DeleteAccountRequest request, Authentication authentication) {
    users.deleteAccount(CurrentUser.id(authentication), request);
  }
}
