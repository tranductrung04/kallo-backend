package com.healthcare.kallo.service.auth;

import com.healthcare.kallo.dto.request.auth.DeleteAccountRequest;
import com.healthcare.kallo.dto.request.auth.UpdateUserRequest;
import com.healthcare.kallo.dto.response.auth.AvatarResponse;
import com.healthcare.kallo.dto.response.auth.UserProfileResponse;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public interface UserService {
  UserProfileResponse getMe(UUID userId);

  UserProfileResponse updateMe(UUID userId, UpdateUserRequest request);

  AvatarResponse updateAvatar(UUID userId, MultipartFile file);

  void deleteAccount(UUID userId, DeleteAccountRequest request);
}
