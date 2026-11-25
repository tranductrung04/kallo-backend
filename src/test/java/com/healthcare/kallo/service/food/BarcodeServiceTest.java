package com.healthcare.kallo.service.food;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.dto.response.food.FoodResponse;
import com.healthcare.kallo.model.food.BarcodeMiss;
import com.healthcare.kallo.model.food.Food;
import com.healthcare.kallo.model.food.enums.FoodCategory;
import com.healthcare.kallo.model.food.enums.FoodSource;
import com.healthcare.kallo.repository.food.BarcodeMissRepository;
import com.healthcare.kallo.repository.food.FoodIngredientRepository;
import com.healthcare.kallo.repository.food.FoodRepository;
import com.healthcare.kallo.repository.food.IngredientRepository;
import com.healthcare.kallo.service.food.client.OpenFoodFactsClient;
import com.healthcare.kallo.service.food.impl.BarcodeServiceImpl;
import com.healthcare.kallo.service.food.impl.FoodServiceImpl;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BarcodeServiceTest {

  @Mock private FoodRepository foodRepository;
  @Mock private BarcodeMissRepository barcodeMissRepository;
  @Mock private OpenFoodFactsClient openFoodFactsClient;
  @Mock private IngredientRepository ingredientRepository;
  @Mock private FoodIngredientRepository foodIngredientRepository;

  private final FoodNutritionCalculator nutritionCalculator = new FoodNutritionCalculator();
  private BarcodeServiceImpl barcodeService;
  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    FoodServiceImpl foodService = new FoodServiceImpl(foodRepository, ingredientRepository, foodIngredientRepository, nutritionCalculator);
    barcodeService = new BarcodeServiceImpl(foodRepository, barcodeMissRepository, openFoodFactsClient, foodService);
  }

  @Test
  @DisplayName("testReadThroughCache_LocalHit: quét mã vạch có sẵn trong CSDL nội bộ phản hồi ngay, không gọi OFF")
  void testReadThroughCache_LocalHit() {
    String barcode = "8934567890123";
    Food food = new Food();
    food.setId(UUID.randomUUID());
    food.setName("Sữa tươi tiệt trùng");
    food.setCategory(FoodCategory.OTHER);
    food.setDefaultServingName("1 hộp (180ml)");
    food.setDefaultServingGrams(new BigDecimal("180.0"));
    food.setCaloriesPer100g(new BigDecimal("70.0"));
    food.setProteinPer100g(new BigDecimal("3.0"));
    food.setCarbsPer100g(new BigDecimal("5.0"));
    food.setFatPer100g(new BigDecimal("3.5"));
    food.setBarcode(barcode);
    food.setSource(FoodSource.OFF);

    when(foodRepository.findByBarcodeAndDeletedAtIsNull(barcode)).thenReturn(Optional.of(food));

    FoodResponse response = barcodeService.getFoodByBarcode(userId, barcode);

    assertThat(response).isNotNull();
    assertThat(response.name()).isEqualTo("Sữa tươi tiệt trùng");
    verifyNoInteractions(barcodeMissRepository);
    verifyNoInteractions(openFoodFactsClient);
  }

  @Test
  @DisplayName("testReadThroughCache_MissCacheHit: mã vạch nằm trong cache miss còn hạn thì ném lỗi ngay mà không gọi OFF")
  void testReadThroughCache_MissCacheHit() {
    String barcode = "8930000000000";

    when(foodRepository.findByBarcodeAndDeletedAtIsNull(barcode)).thenReturn(Optional.empty());
    when(barcodeMissRepository.findActiveMiss(eq(barcode), any(Instant.class)))
        .thenReturn(Optional.of(new BarcodeMiss(barcode, Instant.now().plusSeconds(3600))));

    assertThatThrownBy(() -> barcodeService.getFoodByBarcode(userId, barcode))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.BARCODE_NOT_FOUND);

    verifyNoInteractions(openFoodFactsClient);
  }

  @Test
  @DisplayName("testReadThroughCache_FetchOFFSuccess: mã chưa có, gọi OFF thành công thì lưu vào foods và trả về")
  void testReadThroughCache_FetchOFFSuccess() {
    String barcode = "8936036010014";

    when(foodRepository.findByBarcodeAndDeletedAtIsNull(barcode)).thenReturn(Optional.empty());
    when(barcodeMissRepository.findActiveMiss(eq(barcode), any(Instant.class))).thenReturn(Optional.empty());

    OpenFoodFactsClient.OffProductDto offDto = new OpenFoodFactsClient.OffProductDto(
        barcode,
        "Mì Hảo Hảo tôm chua cay",
        new BigDecimal("450.0"),
        new BigDecimal("8.0"),
        new BigDecimal("60.0"),
        new BigDecimal("18.0"),
        new BigDecimal("3.0"),
        new BigDecimal("2.0"),
        "1 gói (75g)",
        new BigDecimal("75.0"),
        "http://example.com/haohao.jpg"
    );

    when(openFoodFactsClient.fetchProduct(barcode)).thenReturn(Optional.of(offDto));
    when(foodRepository.save(any(Food.class))).thenAnswer(invocation -> {
      Food f = invocation.getArgument(0);
      f.setId(UUID.randomUUID());
      return f;
    });

    FoodResponse response = barcodeService.getFoodByBarcode(userId, barcode);

    assertThat(response).isNotNull();
    assertThat(response.name()).isEqualTo("Mì Hảo Hảo tôm chua cay");
    assertThat(response.source()).isEqualTo(FoodSource.OFF);
    verify(foodRepository).save(any(Food.class));
  }

  @Test
  @DisplayName("testReadThroughCache_FetchOFFMiss: gọi OFF không tìm thấy thì lưu miss cache và ném BARCODE_NOT_FOUND")
  void testReadThroughCache_FetchOFFMiss() {
    String barcode = "9999999999999";

    when(foodRepository.findByBarcodeAndDeletedAtIsNull(barcode)).thenReturn(Optional.empty());
    when(barcodeMissRepository.findActiveMiss(eq(barcode), any(Instant.class))).thenReturn(Optional.empty());
    when(openFoodFactsClient.fetchProduct(barcode)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> barcodeService.getFoodByBarcode(userId, barcode))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.BARCODE_NOT_FOUND);

    verify(barcodeMissRepository).save(any(BarcodeMiss.class));
  }

  @Test
  @DisplayName("testInvalidBarcode: barcode trống hoặc chứa ký tự đặc biệt ném INVALID_BARCODE")
  void testInvalidBarcode() {
    assertThatThrownBy(() -> barcodeService.getFoodByBarcode(userId, "  "))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_BARCODE);

    assertThatThrownBy(() -> barcodeService.getFoodByBarcode(userId, "@@#$$%"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_BARCODE);
  }
}
