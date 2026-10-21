package com.healthcare.kallo.service;

import com.healthcare.kallo.dto.request.DeleteAccountRequest;
import com.healthcare.kallo.dto.request.UpdateUserRequest;
import com.healthcare.kallo.dto.response.AvatarResponse;
import com.healthcare.kallo.dto.response.UserProfileResponse;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public interface UserService {
  UserProfileResponse getMe(UUID userId);

  UserProfileResponse updateMe(UUID userId, UpdateUserRequest request);

  AvatarResponse updateAvatar(UUID userId, MultipartFile file);

  void deleteAccount(UUID userId, DeleteAccountRequest request);
}
