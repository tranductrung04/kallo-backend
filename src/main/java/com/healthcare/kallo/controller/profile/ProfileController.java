package com.healthcare.kallo.controller.profile;

import com.healthcare.kallo.common.ApiResponse;
import com.healthcare.kallo.dto.request.profile.ProfileRequestDto;
import com.healthcare.kallo.dto.response.profile.ProfileResponseDto;
import com.healthcare.kallo.security.auth.CurrentUser;
import com.healthcare.kallo.service.profile.ProfileFacade;
import com.healthcare.kallo.service.profile.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/me/profile")
@Tag(name = "Health Profile", description = "User health profile and biological metrics")
public class ProfileController {

  private final ProfileService profileService;
  private final ProfileFacade profileFacade;

  public ProfileController(ProfileService profileService, ProfileFacade profileFacade) {
    this.profileService = profileService;
    this.profileFacade = profileFacade;
  }

  @GetMapping
  @Operation(summary = "Get current user health profile")
  public ApiResponse<ProfileResponseDto> getProfile(Authentication authentication) {
    UUID userId = CurrentUser.id(authentication);
    return ApiResponse.success(profileService.getProfile(userId));
  }

  @PutMapping
  @Operation(summary = "Create or update user health profile")
  public ApiResponse<ProfileResponseDto> upsertProfile(
      @Valid @RequestBody ProfileRequestDto request,
      Authentication authentication) {
    UUID userId = CurrentUser.id(authentication);
    return ApiResponse.success(profileFacade.upsertProfile(userId, request));
  }

  @PostMapping("/recalculate")
  @Operation(summary = "Recalculate user health profile based on latest logged weight")
  public ApiResponse<ProfileResponseDto> recalculate(Authentication authentication) {
    UUID userId = CurrentUser.id(authentication);
    return ApiResponse.success(profileService.recalculate(userId));
  }
}
