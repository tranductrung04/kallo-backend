package com.healthcare.kallo.controller.food;

import com.healthcare.kallo.common.ApiResponse;
import com.healthcare.kallo.dto.response.food.FoodResponse;
import com.healthcare.kallo.service.food.BarcodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/foods/barcode")
@Tag(name = "Barcode Scanner", description = "Quét và tra cứu thực phẩm đóng gói qua mã vạch")
public class BarcodeController {

  private final BarcodeService barcodeService;

  public BarcodeController(BarcodeService barcodeService) {
    this.barcodeService = barcodeService;
  }

  @GetMapping("/{code}")
  @Operation(
      summary = "Tra cứu sản phẩm theo mã vạch",
      description =
          "Cơ chế bộ đệm 3 bước: Tra CSDL nội bộ -> Tra BarcodeMisses -> Gọi Open Food Facts v2 API.")
  public ApiResponse<FoodResponse> getByBarcode(@PathVariable String code) {
    return ApiResponse.success(barcodeService.getFoodByBarcode(code));
  }
}
