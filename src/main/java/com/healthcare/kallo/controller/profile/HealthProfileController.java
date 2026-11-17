package com.healthcare.kallo.controller.profile;

import com.healthcare.kallo.common.ApiResponse;
import com.healthcare.kallo.dto.request.profile.HealthProfileRequest;
import com.healthcare.kallo.dto.response.profile.HealthProfileResponse;
import com.healthcare.kallo.security.auth.CurrentUser;
import com.healthcare.kallo.service.profile.HealthProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/me/profile")
@Tag(name = "Health Profile", description = "User health profile, biometrics, and nutrition goals")
public class HealthProfileController {

  private final HealthProfileService profileService;

  public HealthProfileController(HealthProfileService profileService) {
    this.profileService = profileService;
  }

  @GetMapping
  @Operation(
      summary = "Get current user health profile",
      description = "Retrieves biometric measurements, BMI, BMR, TDEE, calorie target, macros, and water intake goal.")
  public ApiResponse<HealthProfileResponse> getProfile(Authentication authentication) {
    UUID userId = CurrentUser.id(authentication);
    return ApiResponse.success(profileService.getProfile(userId));
  }

  @PutMapping
  @Operation(
      summary = "Create or update user health profile",
      description = "Sets up or modifies personal metrics, automatically recalculating BMI, BMR, TDEE, calorie target, macros, and water goal.")
  public ApiResponse<HealthProfileResponse> upsertProfile(
      @Valid @RequestBody HealthProfileRequest request, Authentication authentication) {
    UUID userId = CurrentUser.id(authentication);
    return ApiResponse.success(profileService.upsertProfile(userId, request));
  }

  @PostMapping("/recalculate")
  @Operation(
      summary = "Apply recommended targets from latest logged weight",
      description = "Recalculates and updates the profile targets based on the latest logged weight and clears recommendation flag.")
  public ApiResponse<HealthProfileResponse> recalculate(Authentication authentication) {
    UUID userId = CurrentUser.id(authentication);
    return ApiResponse.success(profileService.recalculateWithLatestWeight(userId));
  }
}
