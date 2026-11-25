package com.healthcare.kallo.controller.food;

import com.healthcare.kallo.common.ApiResponse;
import com.healthcare.kallo.dto.response.food.FoodResponse;
import com.healthcare.kallo.security.auth.CurrentUser;
import com.healthcare.kallo.service.food.BarcodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/foods/barcode")
@Tag(name = "Barcode", description = "Barcode lookup via Read-Through cache & Open Food Facts")
public class BarcodeController {

  private final BarcodeService barcodeService;

  public BarcodeController(BarcodeService barcodeService) {
    this.barcodeService = barcodeService;
  }

  @GetMapping("/{code}")
  @Operation(summary = "Lookup food item by barcode")
  public ApiResponse<FoodResponse> getByBarcode(
      @PathVariable String code,
      Authentication authentication) {
    UUID currentUserId = CurrentUser.id(authentication);
    return ApiResponse.success(barcodeService.getFoodByBarcode(currentUserId, code));
  }
}
