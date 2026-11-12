package com.healthcare.kallo.controller.sys;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.healthcare.kallo.dto.response.sys.NotificationSettingsResponse;
import com.healthcare.kallo.exception.GlobalExceptionHandler;
import com.healthcare.kallo.model.sys.NotificationSettings;
import com.healthcare.kallo.security.auth.CurrentUser;
import com.healthcare.kallo.service.sys.NotificationSettingsService;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@ExtendWith(MockitoExtension.class)
class NotificationSettingsControllerTest {
  @Mock NotificationSettingsService service;
  MockMvc mvc;
  UUID firstUser;
  UUID secondUser;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.standaloneSetup(new NotificationSettingsController(service))
        .setControllerAdvice(new GlobalExceptionHandler())
        .setValidator(new LocalValidatorFactoryBean())
        .build();
    firstUser = UUID.randomUUID();
    secondUser = UUID.randomUUID();
  }

  @Test
  void settingsAreScopedToAuthenticatedUser() throws Exception {
    when(service.getOrCreate(firstUser)).thenReturn(new NotificationSettings());
    when(service.update(eq(firstUser), any())).thenReturn(new NotificationSettings());
    mvc.perform(get("/api/notification-settings").principal(authentication(firstUser)))
        .andExpect(status().isOk());
    mvc.perform(put("/api/notification-settings")
            .principal(authentication(firstUser))
            .contentType("application/json")
            .content(validSettings("en")))
        .andExpect(status().isOk());
    verify(service).getOrCreate(firstUser);
    verify(service).update(eq(firstUser), any());
    verify(service, never()).getOrCreate(secondUser);
    verify(service, never()).update(eq(secondUser), any());
  }

  private String validSettings(String language) {
    return """
        {
          "language":"%s","pushEnabled":true,"waterReminderEnabled":true,
          "waterStartTime":"10:00","waterEndTime":"18:00","waterIntervalMinutes":240,
          "mealReminderEnabled":true,"mealBreakfastTime":null,"mealLunchTime":null,"mealDinnerTime":null,
          "calorieAlertEnabled":true,"calorieAlertThresholdPct":100,"socialEnabled":true,
          "dndEnabled":false,"dndStartTime":"22:00","dndEndTime":"07:00"
        }
        """.formatted(language);
  }

  @Test
  void incompletePutReturnsValidationDetails() throws Exception {
    mvc.perform(put("/api/notification-settings")
            .principal(authentication(firstUser))
            .contentType("application/json")
            .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.details.pushEnabled").exists())
        .andExpect(jsonPath("$.error.details.waterIntervalMinutes").exists());
    verifyNoInteractions(service);
  }

  private UsernamePasswordAuthenticationToken authentication(UUID userId) {
    Jwt jwt = Jwt.withTokenValue("token").subject(userId.toString()).header("alg", "none").build();
    return new UsernamePasswordAuthenticationToken(jwt, "token");
  }
}
