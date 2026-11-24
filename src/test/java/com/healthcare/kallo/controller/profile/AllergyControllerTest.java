package com.healthcare.kallo.controller.profile;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.healthcare.kallo.config.RateLimitTestConfig;
import com.healthcare.kallo.dto.response.profile.UserAllergyResponseDto;
import com.healthcare.kallo.service.profile.AllergyService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(value = AllergyController.class, properties = "spring.profiles.active=test")
@Import(RateLimitTestConfig.class)
@AutoConfigureMockMvc
class AllergyControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AllergyService allergyService;

  private final UUID userId = UUID.randomUUID();

  private Jwt userJwt() {
    return Jwt.withTokenValue("mock-token")
        .header("alg", "none")
        .subject(userId.toString())
        .claim("email_verified", true)
        .build();
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor auth() {
    return SecurityMockMvcRequestPostProcessors.jwt().jwt(userJwt());
  }

  @Test
  @DisplayName("GET /api/v1/users/me/allergies - lấy dị ứng của user thành công")
  void getUserAllergies_success() throws Exception {
    UserAllergyResponseDto dto = new UserAllergyResponseDto(
        UUID.randomUUID(),
        "Peanuts",
        "Dị ứng nhẹ",
        true
    );

    when(allergyService.getUserAllergies(userId)).thenReturn(List.of(dto));

    mockMvc.perform(get("/api/v1/users/me/allergies").with(auth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data[0].name").value("Peanuts"));
  }

  @Test
  @DisplayName("PUT /api/v1/users/me/allergies - cập nhật dị ứng của user thành công")
  void updateUserAllergies_success() throws Exception {
    UUID ingId = UUID.randomUUID();
    UserAllergyResponseDto dto = new UserAllergyResponseDto(
        ingId,
        "Peanuts",
        "Dị ứng nặng",
        true
    );

    when(allergyService.updateUserAllergies(eq(userId), any())).thenReturn(List.of(dto));

    String requestJson = String.format("""
        {
          "allergies": [
            {
              "ingredientId": "%s",
              "note": "Dị ứng nặng"
            }
          ]
        }
        """, ingId);

    mockMvc.perform(put("/api/v1/users/me/allergies")
            .with(auth())
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data[0].name").value("Peanuts"));
  }
}
