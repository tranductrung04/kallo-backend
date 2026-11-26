package com.healthcare.kallo.controller.food;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.config.RateLimitTestConfig;
import com.healthcare.kallo.dto.response.food.FoodResponse;
import com.healthcare.kallo.model.food.enums.FoodCategory;
import com.healthcare.kallo.model.food.enums.FoodSource;
import com.healthcare.kallo.service.food.FoodService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(value = FoodController.class, properties = "spring.profiles.active=test")
@Import(RateLimitTestConfig.class)
@AutoConfigureMockMvc
class FoodControllerTest {

  private static final UUID USER_ID = UUID.randomUUID();

  @Autowired private MockMvc mockMvc;

  @MockitoBean private FoodService foodService;

  private static RequestPostProcessor userJwt() {
    Jwt jwt =
        Jwt.withTokenValue("mock-token")
            .header("alg", "none")
            .subject(USER_ID.toString())
            .claim("email_verified", true)
            .build();
    return SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt);
  }

  @Test
  void uploadCustomFoodImageReturnsUpdatedFoodWithPresignedUrl() throws Exception {
    UUID foodId = UUID.randomUUID();
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "dish.jpg", "image/jpeg", new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff});
    FoodResponse response =
        new FoodResponse(
            foodId,
            "Ức gà áp chảo",
            FoodCategory.MEAT,
            "1 phần",
            new BigDecimal("150.0"),
            new BigDecimal("165.0"),
            new BigDecimal("30.0"),
            new BigDecimal("0.5"),
            new BigDecimal("4.2"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            248,
            new BigDecimal("45.0"),
            new BigDecimal("0.8"),
            new BigDecimal("6.3"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            "http://localhost:9000/kallo-bucket/foods/img.jpg?sig=1",
            null,
            FoodSource.USER_CUSTOM,
            null,
            List.of("LUNCH"),
            null,
            true,
            USER_ID,
            List.of(),
            List.of(),
            Instant.now(),
            Instant.now());

    when(foodService.uploadFoodImage(eq(USER_ID), eq(foodId), any())).thenReturn(response);

    mockMvc
        .perform(multipart("/api/foods/custom/{id}/image", foodId).file(file).with(userJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(foodId.toString()))
        .andExpect(
            jsonPath("$.data.imageUrl")
                .value("http://localhost:9000/kallo-bucket/foods/img.jpg?sig=1"));
  }

  @Test
  void uploadCustomFoodImageMissingFilePartReturnsInvalidFile() throws Exception {
    UUID foodId = UUID.randomUUID();

    mockMvc
        .perform(multipart("/api/foods/custom/{id}/image", foodId).with(userJwt()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_FILE"));
  }

  @Test
  void deleteCustomFoodImageReturnsNoContentAndPropagatesForbiddenError() throws Exception {
    UUID foodId = UUID.randomUUID();

    mockMvc
        .perform(delete("/api/foods/custom/{id}/image", foodId).with(userJwt()))
        .andExpect(status().isNoContent());

    verify(foodService).deleteFoodImage(USER_ID, foodId);

    UUID systemFoodId = UUID.randomUUID();
    doThrow(new BusinessException(ErrorCode.CANNOT_MODIFY_SYSTEM_FOOD))
        .when(foodService)
        .deleteFoodImage(USER_ID, systemFoodId);

    mockMvc
        .perform(delete("/api/foods/custom/{id}/image", systemFoodId).with(userJwt()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("CANNOT_MODIFY_SYSTEM_FOOD"));
  }
}
