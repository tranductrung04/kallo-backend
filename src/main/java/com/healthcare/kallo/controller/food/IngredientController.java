package com.healthcare.kallo.controller.food;

import com.healthcare.kallo.common.ApiResponse;
import com.healthcare.kallo.dto.request.food.CustomIngredientRequest;
import com.healthcare.kallo.dto.response.food.IngredientResponse;
import com.healthcare.kallo.security.auth.CurrentUser;
import com.healthcare.kallo.service.food.IngredientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ingredients")
@Tag(name = "Ingredients", description = "Food and Allergen ingredients dictionary")
public class IngredientController {

  private final IngredientService ingredientService;

  public IngredientController(IngredientService ingredientService) {
    this.ingredientService = ingredientService;
  }

  @GetMapping
  @Operation(summary = "Search ingredients or list allergen ingredients")
  public ApiResponse<List<IngredientResponse>> search(
      @RequestParam(required = false) String q,
      @RequestParam(defaultValue = "false") boolean allergenOnly,
      Authentication authentication) {
    UUID currentUserId = CurrentUser.id(authentication);
    return ApiResponse.success(ingredientService.searchIngredients(currentUserId, q, allergenOnly));
  }

  @PostMapping("/custom")
  @Operation(summary = "Create user custom ingredient")
  public ResponseEntity<ApiResponse<IngredientResponse>> createCustom(
      @Valid @RequestBody CustomIngredientRequest request,
      Authentication authentication) {
    UUID currentUserId = CurrentUser.id(authentication);
    IngredientResponse response = ingredientService.createCustomIngredient(currentUserId, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
  }

  @PutMapping("/custom/{id}")
  @Operation(summary = "Update user custom ingredient and recalculate recipe snapshots")
  public ApiResponse<IngredientResponse> updateCustom(
      @PathVariable UUID id,
      @Valid @RequestBody CustomIngredientRequest request,
      Authentication authentication) {
    UUID currentUserId = CurrentUser.id(authentication);
    return ApiResponse.success(ingredientService.updateCustomIngredient(currentUserId, id, request));
  }

  @DeleteMapping("/custom/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Soft delete user custom ingredient")
  public void deleteCustom(@PathVariable UUID id, Authentication authentication) {
    UUID currentUserId = CurrentUser.id(authentication);
    ingredientService.deleteCustomIngredient(currentUserId, id);
  }
}
