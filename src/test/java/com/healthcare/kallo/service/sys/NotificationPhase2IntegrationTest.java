package com.healthcare.kallo.service.sys;

import static org.assertj.core.api.Assertions.assertThat;

import com.healthcare.kallo.model.sys.Notification;
import com.healthcare.kallo.model.sys.enums.NotificationType;
import com.healthcare.kallo.repository.sys.NotificationRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class NotificationPhase2IntegrationTest {
  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.task.execution.pool.core-size", () -> "1");
  }

  @Autowired NotificationService service;
  @Autowired NotificationRepository notifications;
  @Autowired NotificationQuery notificationQuery;
  @Autowired JdbcTemplate jdbc;

  private UUID userId;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    jdbc.update(
        "insert into users (id, email, password_hash, auth_provider, display_name) "
            + "values (?, ?, ?, 'LOCAL', 'Phase 2')",
        userId, userId + "@example.com", "hash");
  }

  @Test
  void postgresNativeInsertJsonbAndDedupeAreReal() {
    NotifyCommand command =
        new NotifyCommand(
            userId,
            NotificationType.SYSTEM,
            Map.of("message", "Welcome"),
            Map.of("notificationId", "abc"),
            "same-key");

    assertThat(service.notify(command)).isEqualTo(NotifyOutcome.SENT);
    assertThat(service.notify(command)).isEqualTo(NotifyOutcome.DUPLICATE);

    List<Notification> rows = notifications.findAll();
    assertThat(rows).hasSize(1);
    assertThat(rows.getFirst().getData()).containsEntry("notificationId", "abc");
  }

  @Test
  void softDeleteKeepsDedupeAndUsersCannotReadEachOther() {
    String key = "persistent-key";
    NotifyCommand command =
        new NotifyCommand(userId, NotificationType.SYSTEM, Map.of(), Map.of(), key);
    assertThat(service.notify(command)).isEqualTo(NotifyOutcome.SENT);
    UUID notificationId =
        notifications.findAll().stream()
            .filter(notification -> notification.getUserId().equals(userId))
            .findFirst()
            .orElseThrow()
            .getId();

    notificationQuery.delete(userId, notificationId);
    assertThat(service.notify(command)).isEqualTo(NotifyOutcome.DUPLICATE);
    assertThat(notificationQuery.page(userId, null, 50, false).items()).isEmpty();

    UUID otherUser = UUID.randomUUID();
    jdbc.update(
        "insert into users (id, email, password_hash, auth_provider, display_name) "
            + "values (?, ?, ?, 'LOCAL', 'Other')",
        otherUser, otherUser + "@example.com", "hash");
    service.notify(new NotifyCommand(otherUser, NotificationType.SYSTEM, Map.of(), Map.of(), "other"));
    assertThat(notificationQuery.page(userId, null, 50, false).items()).isEmpty();
    assertThat(notificationQuery.unreadCount(otherUser)).isEqualTo(1);
  }

  @Test
  void readAllOnlyChangesTheCallingUser() {
    service.notify(new NotifyCommand(userId, NotificationType.SYSTEM, Map.of(), Map.of(), "mine"));
    UUID otherUser = UUID.randomUUID();
    jdbc.update(
        "insert into users (id, email, password_hash, auth_provider, display_name) "
            + "values (?, ?, ?, 'LOCAL', 'Other 2')",
        otherUser, otherUser + "@example.com", "hash");
    service.notify(new NotifyCommand(otherUser, NotificationType.SYSTEM, Map.of(), Map.of(), "theirs"));

    notificationQuery.markAllRead(userId);

    assertThat(notificationQuery.unreadCount(userId)).isZero();
    assertThat(notificationQuery.unreadCount(otherUser)).isEqualTo(1);
  }

  @Test
  void cursorRemainsStableWhenNewNotificationIsInserted() {
    service.notify(new NotifyCommand(userId, NotificationType.SYSTEM, Map.of(), Map.of(), "one"));
    service.notify(new NotifyCommand(userId, NotificationType.SYSTEM, Map.of(), Map.of(), "two"));
    var firstPage = notificationQuery.page(userId, null, 1, false);
    UUID firstId = firstPage.items().getFirst().id();

    service.notify(new NotifyCommand(userId, NotificationType.SYSTEM, Map.of(), Map.of(), "new"));
    var secondPage = notificationQuery.page(userId, firstPage.nextCursor(), 1, false);

    assertThat(secondPage.items()).isNotEmpty();
    assertThat(secondPage.items()).noneMatch(item -> item.id().equals(firstId));
  }
}
