package com.healthcare.kallo.controller.profile;

import com.healthcare.kallo.common.ApiResponse;
import com.healthcare.kallo.dto.request.profile.UpdateUserChronicConditionsRequest;
import com.healthcare.kallo.dto.response.profile.ChronicConditionResponseDto;
import com.healthcare.kallo.dto.response.profile.UserChronicConditionResponseDto;
import com.healthcare.kallo.security.auth.CurrentUser;
import com.healthcare.kallo.service.profile.ChronicConditionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Chronic Conditions", description = "Chronic condition management")
public class ChronicConditionController {

  private final ChronicConditionService conditionService;

  public ChronicConditionController(ChronicConditionService conditionService) {
    this.conditionService = conditionService;
  }

  @GetMapping("/api/v1/chronic-conditions")
  @Operation(summary = "Get list of active chronic conditions in system")
  public ApiResponse<List<ChronicConditionResponseDto>> getSystemConditions() {
    return ApiResponse.success(conditionService.getAllActiveConditions());
  }

  @GetMapping("/api/v1/users/me/chronic-conditions")
  @Operation(summary = "Get user's chronic conditions")
  public ApiResponse<List<UserChronicConditionResponseDto>> getUserConditions(Authentication authentication) {
    UUID userId = CurrentUser.id(authentication);
    return ApiResponse.success(conditionService.getUserChronicConditions(userId));
  }

  @PutMapping("/api/v1/users/me/chronic-conditions")
  @Operation(summary = "Update user's chronic conditions")
  public ApiResponse<List<UserChronicConditionResponseDto>> updateUserConditions(
      @Valid @RequestBody UpdateUserChronicConditionsRequest request,
      Authentication authentication) {
    UUID userId = CurrentUser.id(authentication);
    return ApiResponse.success(conditionService.updateUserChronicConditions(userId, request));
  }
}
