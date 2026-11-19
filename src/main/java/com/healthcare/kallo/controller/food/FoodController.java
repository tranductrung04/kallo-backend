package com.healthcare.kallo.controller.food;

import com.healthcare.kallo.common.ApiResponse;
import com.healthcare.kallo.common.PageResponse;
import com.healthcare.kallo.dto.request.food.CustomFoodRequest;
import com.healthcare.kallo.dto.response.food.FoodResponse;
import com.healthcare.kallo.dto.response.food.FoodSearchItemResponse;
import com.healthcare.kallo.model.food.enums.FoodCategory;
import com.healthcare.kallo.security.auth.CurrentUser;
import com.healthcare.kallo.service.food.FoodService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/foods")
@Tag(name = "Food Data", description = "Tìm kiếm, quản lý thực phẩm hệ thống và món ăn tùy chỉnh")
public class FoodController {

  private final FoodService foodService;

  public FoodController(FoodService foodService) {
    this.foodService = foodService;
  }

  @GetMapping("/search")
  @Operation(
      summary = "Tìm kiếm món ăn tiếng Việt không dấu",
      description =
          "Tìm kiếm món ăn theo tên (hỗ trợ có/không dấu với pg_trgm), lọc theo nhóm món, trả về cả món hệ thống và món riêng của người dùng.")
  public ApiResponse<PageResponse<FoodSearchItemResponse>> searchFoods(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) FoodCategory category,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      Authentication authentication) {
    UUID userId = CurrentUser.id(authentication);
    PageRequest pageable = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)));
    return ApiResponse.success(foodService.searchFoods(userId, q, category, pageable));
  }

  @GetMapping("/{id}")
  @Operation(
      summary = "Lấy chi tiết món ăn theo ID",
      description = "Trả về đầy đủ thông số calo, đạm, đường, xơ, dị ứng và khẩu phần chuẩn.")
  public ApiResponse<FoodResponse> getFoodById(
      @PathVariable UUID id, Authentication authentication) {
    UUID userId = CurrentUser.id(authentication);
    return ApiResponse.success(foodService.getFoodById(id, userId));
  }

  @GetMapping("/vietfood/{classId}")
  @Operation(
      summary = "Tra cứu món ăn theo mã lớp VietFood (0..67)",
      description = "Dành cho việc tích hợp mô hình nhận diện ảnh AI YOLO.")
  public ApiResponse<FoodResponse> getByVietfoodClassId(@PathVariable int classId) {
    return ApiResponse.success(foodService.getFoodByVietfoodClassId(classId));
  }

  @PostMapping("/custom")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Tạo món ăn tùy chỉnh",
      description = "Người dùng tự nhập món ăn riêng với các chỉ số dinh dưỡng.")
  public ApiResponse<FoodResponse> createCustomFood(
      @Valid @RequestBody CustomFoodRequest request, Authentication authentication) {
    UUID userId = CurrentUser.id(authentication);
    return ApiResponse.success(foodService.createCustomFood(userId, request));
  }

  @PutMapping("/custom/{id}")
  @Operation(
      summary = "Cập nhật món ăn tùy chỉnh",
      description = "Chỉ người sở hữu mới có quyền chỉnh sửa món ăn của mình.")
  public ApiResponse<FoodResponse> updateCustomFood(
      @PathVariable UUID id,
      @Valid @RequestBody CustomFoodRequest request,
      Authentication authentication) {
    UUID userId = CurrentUser.id(authentication);
    return ApiResponse.success(foodService.updateCustomFood(userId, id, request));
  }

  @DeleteMapping("/custom/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Xóa món ăn tùy chỉnh",
      description = "Chỉ người sở hữu mới có quyền xóa món ăn của mình.")
  public ApiResponse<Void> deleteCustomFood(
      @PathVariable UUID id, Authentication authentication) {
    UUID userId = CurrentUser.id(authentication);
    foodService.deleteCustomFood(userId, id);
    return ApiResponse.success(null);
  }
}
