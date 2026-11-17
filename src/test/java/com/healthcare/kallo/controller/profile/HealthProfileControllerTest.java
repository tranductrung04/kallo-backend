package com.healthcare.kallo.controller.profile;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.healthcare.kallo.config.RateLimitTestConfig;
import com.healthcare.kallo.dto.request.profile.HealthProfileRequest;
import com.healthcare.kallo.dto.response.profile.HealthProfileResponse;
import com.healthcare.kallo.model.profile.enums.ActivityLevel;
import com.healthcare.kallo.model.profile.enums.BmiCategory;
import com.healthcare.kallo.model.profile.enums.Gender;
import com.healthcare.kallo.model.profile.enums.GoalType;
import com.healthcare.kallo.service.profile.HealthProfileService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
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

@WebMvcTest(value = HealthProfileController.class, properties = "spring.profiles.active=test")
@Import(RateLimitTestConfig.class)
@AutoConfigureMockMvc
class HealthProfileControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private HealthProfileService profileService;

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
  void getProfile_returnsOk() throws Exception {
    UUID userId = UUID.randomUUID();
    HealthProfileResponse response =
        new HealthProfileResponse(
            UUID.randomUUID(),
            userId,
            Gender.MALE,
            LocalDate.of(1998, 5, 15),
            28,
            BigDecimal.valueOf(175.0),
            BigDecimal.valueOf(70.0),
            ActivityLevel.MODERATELY_ACTIVE,
            GoalType.LOSE_WEIGHT,
            BigDecimal.valueOf(65.0),
            BigDecimal.valueOf(0.5),
            BigDecimal.valueOf(22.9),
            BmiCategory.NORMAL,
            "Bình thường",
            1674,
            2595,
            2045,
            false,
            BigDecimal.valueOf(153.4),
            BigDecimal.valueOf(204.5),
            BigDecimal.valueOf(68.2),
            BigDecimal.valueOf(30.0),
            BigDecimal.valueOf(40.0),
            BigDecimal.valueOf(30.0),
            false,
            2450,
            List.of("PEANUT"),
            false,
            null,
            null,
            null,
            Instant.now(),
            Instant.now());

    when(profileService.getProfile(userId)).thenReturn(response);

    mockMvc
        .perform(get("/api/users/me/profile").with(userJwt(userId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.dailyCalorieGoal").value(2045))
        .andExpect(jsonPath("$.data.bmiCategory").value("NORMAL"))
        .andExpect(jsonPath("$.data.allergies[0]").value("PEANUT"));
  }

  @Test
  void upsertProfile_validRequest_returnsOk() throws Exception {
    UUID userId = UUID.randomUUID();
    HealthProfileResponse response =
        new HealthProfileResponse(
            UUID.randomUUID(),
            userId,
            Gender.FEMALE,
            LocalDate.of(2000, 1, 1),
            26,
            BigDecimal.valueOf(162.0),
            BigDecimal.valueOf(52.0),
            ActivityLevel.LIGHTLY_ACTIVE,
            GoalType.MAINTAIN_WEIGHT,
            BigDecimal.valueOf(52.0),
            BigDecimal.ZERO,
            BigDecimal.valueOf(19.8),
            BmiCategory.NORMAL,
            "Bình thường",
            1260,
            1733,
            1733,
            false,
            BigDecimal.valueOf(86.7),
            BigDecimal.valueOf(216.6),
            BigDecimal.valueOf(57.8),
            BigDecimal.valueOf(20.0),
            BigDecimal.valueOf(50.0),
            BigDecimal.valueOf(30.0),
            false,
            1820,
            List.of(),
            false,
            null,
            null,
            null,
            Instant.now(),
            Instant.now());

    when(profileService.upsertProfile(eq(userId), any(HealthProfileRequest.class)))
        .thenReturn(response);

    String json =
        """
        {
          "gender": "FEMALE",
          "birthDate": "2000-01-01",
          "heightCm": 162.0,
          "weightKg": 52.0,
          "activityLevel": "LIGHTLY_ACTIVE",
          "goalType": "MAINTAIN_WEIGHT"
        }
        """;

    mockMvc
        .perform(
            put("/api/users/me/profile")
                .with(userJwt(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.dailyCalorieGoal").value(1733));
  }

  @Test
  void upsertProfile_invalidHeight_returnsBadRequest() throws Exception {
    UUID userId = UUID.randomUUID();

    String json =
        """
        {
          "gender": "MALE",
          "birthDate": "2000-01-01",
          "heightCm": 80.0,
          "weightKg": 70.0,
          "activityLevel": "SEDENTARY",
          "goalType": "MAINTAIN_WEIGHT"
        }
        """;

    mockMvc
        .perform(
            put("/api/users/me/profile")
                .with(userJwt(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.details.heightCm").exists());
  }

  @Test
  void recalculate_returnsOk() throws Exception {
    UUID userId = UUID.randomUUID();
    HealthProfileResponse response =
        new HealthProfileResponse(
            UUID.randomUUID(),
            userId,
            Gender.MALE,
            LocalDate.of(1998, 5, 15),
            28,
            BigDecimal.valueOf(175.0),
            BigDecimal.valueOf(68.0),
            ActivityLevel.MODERATELY_ACTIVE,
            GoalType.LOSE_WEIGHT,
            BigDecimal.valueOf(65.0),
            BigDecimal.valueOf(0.5),
            BigDecimal.valueOf(22.2),
            BmiCategory.NORMAL,
            "Bình thường",
            1654,
            2564,
            2014,
            false,
            BigDecimal.valueOf(151.1),
            BigDecimal.valueOf(201.4),
            BigDecimal.valueOf(67.1),
            BigDecimal.valueOf(30.0),
            BigDecimal.valueOf(40.0),
            BigDecimal.valueOf(30.0),
            false,
            2380,
            List.of(),
            false,
            null,
            null,
            null,
            Instant.now(),
            Instant.now());

    when(profileService.recalculateWithLatestWeight(userId)).thenReturn(response);

    mockMvc
        .perform(post("/api/users/me/profile/recalculate").with(userJwt(userId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.weightKg").value(68.0));
  }

  @Test
  void unauthenticated_returnsUnauthorized() throws Exception {
    mockMvc.perform(get("/api/users/me/profile")).andExpect(status().isUnauthorized());
  }
}
