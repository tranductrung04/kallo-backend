package com.healthcare.kallo.controller.profile;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.healthcare.kallo.config.RateLimitTestConfig;
import com.healthcare.kallo.dto.response.profile.MacronutrientDto;
import com.healthcare.kallo.dto.response.profile.ProfileResponseDto;
import com.healthcare.kallo.model.profile.enums.ActivityLevel;
import com.healthcare.kallo.model.profile.enums.Gender;
import com.healthcare.kallo.model.profile.enums.GoalType;
import com.healthcare.kallo.service.profile.ProfileFacade;
import com.healthcare.kallo.service.profile.ProfileService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(value = ProfileController.class, properties = "spring.profiles.active=test")
@Import(RateLimitTestConfig.class)
@AutoConfigureMockMvc
class ProfileControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ProfileService profileService;
  @MockitoBean private ProfileFacade profileFacade;

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
  @DisplayName("GET /api/v1/users/me/profile - thành công trả về 200 OK")
  void getProfile_success() throws Exception {
    ProfileResponseDto response = new ProfileResponseDto(
        userId,
        Gender.FEMALE,
        LocalDate.of(1995, 8, 15),
        31,
        new BigDecimal("162.5"),
        new BigDecimal("58.0"),
        new BigDecimal("22.0"),
        ActivityLevel.MODERATELY_ACTIVE,
        GoalType.LOSE_WEIGHT,
        new BigDecimal("53.0"),
        new BigDecimal("0.50"),
        new BigDecimal("1317.88"),
        new BigDecimal("2042.71"),
        new BigDecimal("1492.71"),
        new MacronutrientDto(new BigDecimal("116.0"), new BigDecimal("41.46"), new BigDecimal("163.89")),
        new BigDecimal("2.03"),
        false,
        false,
        null,
        null,
        new BigDecimal("58.0"),
        null,
        1L
    );

    when(profileService.getProfile(userId)).thenReturn(response);

    mockMvc.perform(get("/api/v1/users/me/profile")
            .with(auth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.userId").value(userId.toString()))
        .andExpect(jsonPath("$.data.gender").value("FEMALE"))
        .andExpect(jsonPath("$.data.bmr").value(1317.88));
  }

  @Test
  @DisplayName("PUT /api/v1/users/me/profile - thành công trả về 200 OK")
  void upsertProfile_success() throws Exception {
    ProfileResponseDto response = new ProfileResponseDto(
        userId,
        Gender.FEMALE,
        LocalDate.of(1995, 8, 15),
        31,
        new BigDecimal("162.5"),
        new BigDecimal("58.0"),
        new BigDecimal("22.0"),
        ActivityLevel.MODERATELY_ACTIVE,
        GoalType.LOSE_WEIGHT,
        new BigDecimal("53.0"),
        new BigDecimal("0.50"),
        new BigDecimal("1317.88"),
        new BigDecimal("2042.71"),
        new BigDecimal("1492.71"),
        new MacronutrientDto(new BigDecimal("116.0"), new BigDecimal("41.46"), new BigDecimal("163.89")),
        new BigDecimal("2.03"),
        false,
        false,
        null,
        null,
        new BigDecimal("58.0"),
        null,
        1L
    );

    when(profileFacade.upsertProfile(eq(userId), any())).thenReturn(response);

    String requestJson = """
        {
          "gender": "FEMALE",
          "dob": "1995-08-15",
          "heightCm": 162.5,
          "weightKg": 58.0,
          "activityLevel": "MODERATELY_ACTIVE",
          "goalType": "LOSE_WEIGHT",
          "targetWeightKg": 53.0,
          "weeklyRateKg": 0.50
        }
        """;

    mockMvc.perform(put("/api/v1/users/me/profile")
            .with(auth())
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.tdee").value(2042.71));
  }

  @Test
  @DisplayName("POST /api/v1/users/me/profile/recalculate - thành công trả về 200 OK")
  void recalculate_success() throws Exception {
    ProfileResponseDto response = new ProfileResponseDto(
        userId,
        Gender.FEMALE,
        LocalDate.of(1995, 8, 15),
        31,
        new BigDecimal("162.5"),
        new BigDecimal("58.0"),
        new BigDecimal("22.0"),
        ActivityLevel.MODERATELY_ACTIVE,
        GoalType.MAINTAIN_WEIGHT,
        null,
        new BigDecimal("0.00"),
        new BigDecimal("1317.88"),
        new BigDecimal("2042.71"),
        new BigDecimal("2042.71"),
        new MacronutrientDto(new BigDecimal("116.0"), new BigDecimal("56.74"), new BigDecimal("267.01")),
        new BigDecimal("2.03"),
        false,
        false,
        null,
        null,
        new BigDecimal("58.0"),
        "TARGET_REACHED",
        2L
    );

    when(profileService.recalculate(userId)).thenReturn(response);

    mockMvc.perform(post("/api/v1/users/me/profile/recalculate")
            .with(auth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.goalAdvice").value("TARGET_REACHED"));
  }
}
