package com.healthcare.kallo.dto.request.food;

import com.healthcare.kallo.model.food.enums.FoodCategory;
import com.healthcare.kallo.model.food.enums.MealType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public record CustomFoodRequest(
    @NotBlank(message = "Tên món ăn không được để trống")
    @Size(max = 255, message = "Tên món không được vượt quá 255 ký tự")
    @Schema(description = "Tên món ăn tiếng Việt", example = "Cơm sườn nướng mật ong")
    String name,

    @NotNull(message = "Nhóm món ăn không được để trống")
    @Schema(description = "Phân loại nhóm món", example = "RICE_DISH")
    FoodCategory category,

    @NotBlank(message = "Tên khẩu phần chuẩn không được để trống")
    @Size(max = 100, message = "Tên khẩu phần không được vượt quá 100 ký tự")
    @Schema(description = "Tên khẩu phần đời thực", example = "1 dĩa vừa")
    String defaultServingName,

    @NotNull(message = "Khối lượng khẩu phần không được để trống")
    @DecimalMin(value = "1.0", message = "Khối lượng khẩu phần phải lớn hơn 0")
    @DecimalMax(value = "5000.0", message = "Khối lượng khẩu phần tối đa 5000g")
    @Schema(description = "Khối lượng tương đương (gam) của 1 khẩu phần", example = "350.0")
    BigDecimal defaultServingGrams,

    @NotNull(message = "Năng lượng trên 100g không được để trống")
    @DecimalMin(value = "0.0", message = "Calo không được âm")
    @DecimalMax(value = "1000.0", message = "Calo trên 100g tối đa 1000 kcal")
    @Schema(description = "Calo trên 100g", example = "195.0")
    BigDecimal caloriesPer100g,

    @NotNull(message = "Chất đạm trên 100g không được để trống")
    @DecimalMin(value = "0.0", message = "Chất đạm không được âm")
    @DecimalMax(value = "100.0", message = "Chất đạm tối đa 100g")
    @Schema(description = "Chất đạm trên 100g", example = "12.5")
    BigDecimal proteinPer100g,

    @NotNull(message = "Chất bột đường trên 100g không được để trống")
    @DecimalMin(value = "0.0", message = "Carbs không được âm")
    @DecimalMax(value = "100.0", message = "Carbs tối đa 100g")
    @Schema(description = "Carbs trên 100g", example = "25.0")
    BigDecimal carbsPer100g,

    @NotNull(message = "Chất béo trên 100g không được để trống")
    @DecimalMin(value = "0.0", message = "Chất béo không được âm")
    @DecimalMax(value = "100.0", message = "Chất béo tối đa 100g")
    @Schema(description = "Chất béo trên 100g", example = "5.5")
    BigDecimal fatPer100g,

    @DecimalMin(value = "0.0", message = "Đường không được âm")
    @DecimalMax(value = "100.0", message = "Đường tối đa 100g")
    @Schema(description = "Đường trên 100g (nếu có)", example = "3.2")
    BigDecimal sugarPer100g,

    @DecimalMin(value = "0.0", message = "Chất xơ không được âm")
    @DecimalMax(value = "100.0", message = "Chất xơ tối đa 100g")
    @Schema(description = "Chất xơ trên 100g (nếu có)", example = "1.5")
    BigDecimal fiberPer100g,

    @Schema(description = "Đường dẫn ảnh món ăn", example = "https://cdn.kallo.vn/foods/custom-com-suon.jpg")
    String imageUrl,

    @Schema(description = "Mã vạch sản phẩm (nếu có)", example = "8934567890123")
    String barcode,

    @Schema(description = "Danh mục chất gây dị ứng", example = "[\"PEANUT\"]")
    List<String> allergens,

    @Schema(description = "Các bữa ăn phù hợp cho món này", example = "[\"LUNCH\", \"DINNER\"]")
    List<MealType> suitableMealTypes,

    @PositiveOrZero(message = "Giá tham khảo không được âm")
    @Schema(description = "Giá tham khảo (VNĐ/khẩu phần)", example = "45000")
    Integer referencePriceVnd
) {}
