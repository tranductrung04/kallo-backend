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
import com.healthcare.kallo.repository.food.FoodRepository;
import com.healthcare.kallo.service.food.client.OpenFoodFactsClient;
import com.healthcare.kallo.service.food.impl.BarcodeServiceImpl;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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

  private BarcodeService barcodeService;

  @BeforeEach
  void setUp() {
    barcodeService =
        new BarcodeServiceImpl(foodRepository, barcodeMissRepository, openFoodFactsClient);
  }

  @Test
  @DisplayName("getFoodByBarcode() throws INVALID_BARCODE on malformed input")
  void getFoodByBarcode_invalidInput() {
    assertThatThrownBy(() -> barcodeService.getFoodByBarcode("abc"))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_BARCODE);

    assertThatThrownBy(() -> barcodeService.getFoodByBarcode(""))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_BARCODE);

    assertThatThrownBy(() -> barcodeService.getFoodByBarcode(null))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_BARCODE);
  }

  @Test
  @DisplayName("getFoodByBarcode() returns food immediately when found in local database (Tầng 1)")
  void getFoodByBarcode_localHit() {
    String barcode = "8934567890123";
    Food food = new Food();
    food.setId(UUID.randomUUID());
    food.setName("Sữa tươi tiệt trùng");
    food.setNameUnaccented("sua tuoi tiet trung");
    food.setBarcode(barcode);
    food.setCategory(FoodCategory.DAIRY);
    food.setDefaultServingName("1 hộp (180ml)");
    food.setDefaultServingGrams(BigDecimal.valueOf(180.0));
    food.setCaloriesPer100g(BigDecimal.valueOf(70.0));
    food.setProteinPer100g(BigDecimal.valueOf(3.0));
    food.setCarbsPer100g(BigDecimal.valueOf(5.0));
    food.setFatPer100g(BigDecimal.valueOf(3.5));
    food.setCaloriesPerServing(126);
    food.setProteinPerServing(BigDecimal.valueOf(5.4));
    food.setCarbsPerServing(BigDecimal.valueOf(9.0));
    food.setFatPerServing(BigDecimal.valueOf(6.3));
    food.setSource(FoodSource.SYSTEM);

    when(foodRepository.findByBarcode(barcode)).thenReturn(Optional.of(food));

    FoodResponse response = barcodeService.getFoodByBarcode(barcode);

    assertThat(response).isNotNull();
    assertThat(response.name()).isEqualTo("Sữa tươi tiệt trùng");
    verifyNoInteractions(barcodeMissRepository, openFoodFactsClient);
  }

  @Test
  @DisplayName("getFoodByBarcode() throws BARCODE_NOT_FOUND when barcode is in miss cache (Tầng 2)")
  void getFoodByBarcode_missCacheHit() {
    String barcode = "8934567890123";

    when(foodRepository.findByBarcode(barcode)).thenReturn(Optional.empty());
    when(barcodeMissRepository.findByBarcodeAndExpiresAtAfter(eq(barcode), any(Instant.class)))
        .thenReturn(Optional.of(new BarcodeMiss(barcode, Instant.now().plusSeconds(3600))));

    assertThatThrownBy(() -> barcodeService.getFoodByBarcode(barcode))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BARCODE_NOT_FOUND);

    verifyNoInteractions(openFoodFactsClient);
  }

  @Test
  @DisplayName("getFoodByBarcode() records miss and throws when Open Food Facts returns empty (Tầng 3)")
  void getFoodByBarcode_offNotFound() {
    String barcode = "8934567890123";

    when(foodRepository.findByBarcode(barcode)).thenReturn(Optional.empty());
    when(barcodeMissRepository.findByBarcodeAndExpiresAtAfter(eq(barcode), any(Instant.class)))
        .thenReturn(Optional.empty());
    when(openFoodFactsClient.fetchByBarcode(barcode)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> barcodeService.getFoodByBarcode(barcode))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BARCODE_NOT_FOUND);

    verify(barcodeMissRepository).save(any(BarcodeMiss.class));
    verify(foodRepository, never()).save(any());
  }

  @Test
  @DisplayName("getFoodByBarcode() saves and returns food when Open Food Facts finds valid product (Tầng 3)")
  void getFoodByBarcode_offSuccess() {
    String barcode = "8934567890123";

    OpenFoodFactsClient.OffProduct product =
        new OpenFoodFactsClient.OffProduct(
            barcode,
            "Mì Hảo Hảo Tôm Chua Cay",
            "Mì Hảo Hảo",
            "Acecook",
            "1 gói (75g)",
            75.0,
            "https://off.org/haohao.jpg",
            List.of("en:gluten", "en:crustaceans"),
            Map.of(
                "energy-kcal_100g", 460.0,
                "proteins_100g", 9.0,
                "carbohydrates_100g", 62.0,
                "fat_100g", 19.0,
                "sugars_100g", 4.0,
                "fiber_100g", 2.0));

    when(foodRepository.findByBarcode(barcode)).thenReturn(Optional.empty());
    when(barcodeMissRepository.findByBarcodeAndExpiresAtAfter(eq(barcode), any(Instant.class)))
        .thenReturn(Optional.empty());
    when(openFoodFactsClient.fetchByBarcode(barcode)).thenReturn(Optional.of(product));
    when(foodRepository.save(any(Food.class))).thenAnswer(i -> {
      Food f = i.getArgument(0);
      f.setId(UUID.randomUUID());
      return f;
    });

    FoodResponse response = barcodeService.getFoodByBarcode(barcode);

    assertThat(response).isNotNull();
    assertThat(response.name()).isEqualTo("Mì Hảo Hảo");
    assertThat(response.source()).isEqualTo(FoodSource.OFF);
    assertThat(response.defaultServingGrams()).isEqualByComparingTo(BigDecimal.valueOf(75.0));
    assertThat(response.caloriesPerServing()).isEqualTo((int) Math.round(460.0 * 0.75)); // 345
    assertThat(response.allergens()).contains("GLUTEN", "CRUSTACEANS");
  }
}
