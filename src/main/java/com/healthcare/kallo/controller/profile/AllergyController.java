package com.healthcare.kallo.controller.profile;

import com.healthcare.kallo.common.ApiResponse;
import com.healthcare.kallo.dto.request.profile.UpdateUserAllergiesRequest;
import com.healthcare.kallo.dto.response.profile.UserAllergyResponseDto;
import com.healthcare.kallo.security.auth.CurrentUser;
import com.healthcare.kallo.service.profile.AllergyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/me/allergies")
@Tag(name = "Allergies", description = "User ingredient allergy management")
public class AllergyController {

  private final AllergyService allergyService;

  public AllergyController(AllergyService allergyService) {
    this.allergyService = allergyService;
  }

  @GetMapping
  @Operation(summary = "Get user allergies")
  public ApiResponse<List<UserAllergyResponseDto>> getUserAllergies(Authentication authentication) {
    UUID userId = CurrentUser.id(authentication);
    return ApiResponse.success(allergyService.getUserAllergies(userId));
  }

  @PutMapping
  @Operation(summary = "Update user allergies")
  public ApiResponse<List<UserAllergyResponseDto>> updateUserAllergies(
      @Valid @RequestBody UpdateUserAllergiesRequest request,
      Authentication authentication) {
    UUID userId = CurrentUser.id(authentication);
    return ApiResponse.success(allergyService.updateUserAllergies(userId, request));
  }
}
