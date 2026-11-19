package com.healthcare.kallo.controller.food;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.healthcare.kallo.config.RateLimitTestConfig;
import com.healthcare.kallo.dto.response.food.FoodResponse;
import com.healthcare.kallo.model.food.enums.FoodCategory;
import com.healthcare.kallo.model.food.enums.FoodSource;
import com.healthcare.kallo.model.food.enums.MealType;
import com.healthcare.kallo.service.food.BarcodeService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(value = BarcodeController.class, properties = "spring.profiles.active=test")
@Import(RateLimitTestConfig.class)
@AutoConfigureMockMvc
class BarcodeControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private BarcodeService barcodeService;

  private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor userJwt(UUID userId) {
    return SecurityMockMvcRequestPostProcessors.jwt()
        .jwt(
            Jwt.withTokenValue("token")
                .subject(userId.toString())
                .claim("email_verified", true)
                .header("alg", "none")
                .build());
  }

  @Test
  @DisplayName("GET /api/foods/barcode/{code} returns 200 with product details")
  void getByBarcode_returnsOk() throws Exception {
    UUID userId = UUID.randomUUID();
    String barcode = "8934567890123";

    FoodResponse response =
        new FoodResponse(
            UUID.randomUUID(),
            "Mì Hảo Hảo",
            "mi hao hao",
            FoodCategory.SNACK,
            "1 gói (75g)",
            BigDecimal.valueOf(75.0),
            BigDecimal.valueOf(460.0),
            BigDecimal.valueOf(9.0),
            BigDecimal.valueOf(62.0),
            BigDecimal.valueOf(19.0),
            BigDecimal.valueOf(4.0),
            BigDecimal.valueOf(2.0),
            345,
            BigDecimal.valueOf(6.8),
            BigDecimal.valueOf(46.5),
            BigDecimal.valueOf(14.3),
            BigDecimal.valueOf(3.0),
            BigDecimal.valueOf(1.5),
            "https://off.org/haohao.jpg",
            barcode,
            FoodSource.OFF,
            null,
            List.of("GLUTEN"),
            List.of(MealType.BREAKFAST, MealType.SNACK),
            12000,
            true,
            null,
            Instant.now(),
            Instant.now());

    when(barcodeService.getFoodByBarcode(barcode)).thenReturn(response);

    mockMvc
        .perform(get("/api/foods/barcode/" + barcode).with(userJwt(userId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.name").value("Mì Hảo Hảo"))
        .andExpect(jsonPath("$.data.barcode").value(barcode))
        .andExpect(jsonPath("$.data.caloriesPerServing").value(345));
  }
}
