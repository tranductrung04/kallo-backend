package com.healthcare.kallo.service.sys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.healthcare.kallo.model.auth.User;
import com.healthcare.kallo.model.sys.NotificationSettings;
import com.healthcare.kallo.model.sys.enums.NotificationType;
import com.healthcare.kallo.repository.auth.UserRepository;
import com.healthcare.kallo.repository.sys.NotificationRepository;
import com.healthcare.kallo.service.sys.impl.NotificationServiceImpl;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class NotificationServicePhase3Test {
  @Mock NotificationRepository notifications;
  @Mock EntityManager entityManager;
  @Mock Query query;
  @Mock ApplicationEventPublisher events;
  @Mock ObjectMapper objectMapper;
  @Mock NotificationMessageFactory messages;
  @Mock NotificationPolicy policy;
  @Mock NotificationSettingsService settingsService;
  @Mock UserRepository users;

  NotificationServiceImpl service;
  UUID userId;

  @BeforeEach
  void setUp() {
    service = new NotificationServiceImpl(
        notifications, entityManager, events, objectMapper, messages,
        Clock.fixed(Instant.parse("2026-11-12T12:00:00Z"), ZoneOffset.UTC),
        policy, settingsService, users);
    userId = UUID.randomUUID();
    User user = new User();
    user.setId(userId);
    user.setTimezone("Asia/Ho_Chi_Minh");
    when(users.findById(userId)).thenReturn(Optional.of(user));
    when(settingsService.getOrCreate(userId)).thenReturn(new NotificationSettings());
  }

  @Test
  void disabledTypeDoesNotCreateNotificationOrPublishPushEvent() {
    when(policy.evaluate(any(), eq(NotificationType.WATER_REMINDER), any()))
        .thenReturn(NotifyOutcome.SUPPRESSED_DISABLED);

    assertThat(service.notify(new NotifyCommand(
        userId, NotificationType.WATER_REMINDER, Map.of(), Map.of(), "disabled")))
        .isEqualTo(NotifyOutcome.SUPPRESSED_DISABLED);
    verifyNoInteractions(entityManager, events);
  }

  @Test
  void dndCreatesCenterItemButDoesNotPublishPushEvent() throws Exception {
    when(policy.evaluate(any(), eq(NotificationType.SYSTEM), any()))
        .thenReturn(NotifyOutcome.SUPPRESSED_PUSH_ONLY);
    when(messages.render(eq(NotificationType.SYSTEM), any(), any()))
        .thenReturn(new NotificationContent("title", "body", "push title", "push body"));
    when(objectMapper.writeValueAsString(any())).thenReturn("{}");
    when(entityManager.createNativeQuery(anyString())).thenReturn(query);
    when(query.setParameter(anyString(), any())).thenReturn(query);
    when(query.getSingleResult()).thenReturn(UUID.randomUUID());

    assertThat(service.notify(new NotifyCommand(
        userId, NotificationType.SYSTEM, Map.of(), Map.of(), "dnd")))
        .isEqualTo(NotifyOutcome.SUPPRESSED_PUSH_ONLY);
    verify(entityManager).createNativeQuery(anyString());
    verifyNoInteractions(events);
  }
}
