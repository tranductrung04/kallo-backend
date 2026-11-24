package com.healthcare.kallo.controller.profile;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.healthcare.kallo.config.RateLimitTestConfig;
import com.healthcare.kallo.dto.response.profile.ChronicConditionResponseDto;
import com.healthcare.kallo.dto.response.profile.UserChronicConditionResponseDto;
import com.healthcare.kallo.service.profile.ChronicConditionService;
import java.time.LocalDate;
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

@WebMvcTest(value = ChronicConditionController.class, properties = "spring.profiles.active=test")
@Import(RateLimitTestConfig.class)
@AutoConfigureMockMvc
class ChronicConditionControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ChronicConditionService conditionService;

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
  @DisplayName("GET /api/v1/chronic-conditions - lấy danh mục bệnh thành công")
  void getSystemConditions_success() throws Exception {
    ChronicConditionResponseDto dto = new ChronicConditionResponseDto(
        UUID.randomUUID(),
        "DIABETES_T2",
        "Tiểu đường tuýp 2",
        "Mô tả",
        true
    );

    when(conditionService.getAllActiveConditions()).thenReturn(List.of(dto));

    mockMvc.perform(get("/api/v1/chronic-conditions").with(auth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data[0].code").value("DIABETES_T2"));
  }

  @Test
  @DisplayName("GET /api/v1/users/me/chronic-conditions - lấy bệnh của user thành công")
  void getUserConditions_success() throws Exception {
    UserChronicConditionResponseDto dto = new UserChronicConditionResponseDto(
        UUID.randomUUID(),
        "HYPERTENSION",
        "Tăng huyết áp",
        "Mô tả",
        LocalDate.of(2023, 1, 1),
        "Ghi chú"
    );

    when(conditionService.getUserChronicConditions(userId)).thenReturn(List.of(dto));

    mockMvc.perform(get("/api/v1/users/me/chronic-conditions").with(auth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data[0].code").value("HYPERTENSION"));
  }

  @Test
  @DisplayName("PUT /api/v1/users/me/chronic-conditions - cập nhật bệnh của user thành công")
  void updateUserConditions_success() throws Exception {
    UUID condId = UUID.randomUUID();
    UserChronicConditionResponseDto dto = new UserChronicConditionResponseDto(
        condId,
        "HYPERTENSION",
        "Tăng huyết áp",
        "Mô tả",
        LocalDate.of(2023, 1, 1),
        "Ghi chú"
    );

    when(conditionService.updateUserChronicConditions(eq(userId), any())).thenReturn(List.of(dto));

    String requestJson = String.format("""
        {
          "conditions": [
            {
              "conditionId": "%s",
              "diagnosedAt": "2023-01-01",
              "note": "Ghi chú"
            }
          ]
        }
        """, condId);

    mockMvc.perform(put("/api/v1/users/me/chronic-conditions")
            .with(auth())
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data[0].code").value("HYPERTENSION"));
  }
}
