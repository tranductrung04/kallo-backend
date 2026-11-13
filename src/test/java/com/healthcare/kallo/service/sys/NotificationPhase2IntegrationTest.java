package com.healthcare.kallo.service.sys;

import static org.assertj.core.api.Assertions.assertThat;

import com.healthcare.kallo.event.sys.DailySummaryChangedEvent;
import com.healthcare.kallo.model.sys.Notification;
import com.healthcare.kallo.model.sys.enums.NotificationType;
import com.healthcare.kallo.repository.sys.NotificationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import static org.mockito.Mockito.*;
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
@org.springframework.context.annotation.Import(NotificationPhase2IntegrationTest.TestConfig.class)
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
  @Autowired ApplicationEventPublisher events;
  @Autowired TransactionTemplate transactions;
  @MockitoBean PushDispatcher pushes;

  static final MutableClock CLOCK =
      new MutableClock(Instant.parse("2026-11-12T17:30:00Z"), ZoneId.of("UTC"));

  @TestConfiguration
  static class TestConfig {
    @Bean("testClock")
    @Primary
    Clock clock() {
      return CLOCK;
    }
  }

  private UUID userId;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    jdbc.update(
        "insert into users (id, email, password_hash, auth_provider, display_name) "
            + "values (?, ?, ?, 'LOCAL', 'Phase 2')",
        userId, userId + "@example.com", "hash");
    jdbc.update("insert into notification_settings (user_id) values (?)", userId);
    reset(pushes);
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

  @Test
  void threeCommittedSummaryEventsCreateOneNotificationAndOnePush() {
    publishSummary(2100, 2000);
    publishSummary(2200, 2000);
    publishSummary(2300, 2000);

    assertThat(userNotifications()).hasSize(1);
    verify(pushes, times(1)).dispatch(eq(userId), any(PushMessage.class));
  }

  @Test
  void concurrentSummaryEventsCreateOneNotificationAndOnePush() throws Exception {
    int workers = 2;
    var ready = new CountDownLatch(workers);
    var start = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(workers);
    try {
      var first = executor.submit(() -> publishAtBarrier(ready, start));
      var second = executor.submit(() -> publishAtBarrier(ready, start));
      ready.await();
      start.countDown();
      first.get();
      second.get();
    } finally {
      executor.shutdownNow();
    }

    assertThat(userNotifications()).hasSize(1);
    verify(pushes, times(1)).dispatch(eq(userId), any(PushMessage.class));
  }

  @Test
  void nextLocalDayCreatesAnotherAlert() {
    publishSummary(2100, 2000);
    CLOCK.setInstant(Instant.parse("2026-11-13T17:30:00Z"));
    publishSummary(2100, 2000);

    assertThat(userNotifications()).hasSize(2);
    verify(pushes, times(2)).dispatch(eq(userId), any(PushMessage.class));
  }

  @Test
  void rollbackDoesNotRunAfterCommitListener() {
    transactions.executeWithoutResult(status -> {
      events.publishEvent(summaryEvent());
      status.setRollbackOnly();
    });

    assertThat(userNotifications()).isEmpty();
    verifyNoInteractions(pushes);
  }

  private void publishSummary(double caloriesIn, double calorieGoal) {
    transactions.executeWithoutResult(status ->
        events.publishEvent(new DailySummaryChangedEvent(
            userId, LocalDate.now(CLOCK.withZone(ZoneId.of("Asia/Ho_Chi_Minh"))),
            caloriesIn, calorieGoal)));
  }

  private void publishAtBarrier(CountDownLatch ready, CountDownLatch start) {
    ready.countDown();
    try {
      start.await();
      publishSummary(2100, 2000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private DailySummaryChangedEvent summaryEvent() {
    return new DailySummaryChangedEvent(userId, LocalDate.of(2026, 11, 13), 2100, 2000);
  }

  private List<Notification> userNotifications() {
    return notifications.findAll().stream()
        .filter(notification -> notification.getUserId().equals(userId))
        .toList();
  }

  static final class MutableClock extends Clock {
    private volatile Instant instant;
    private final ZoneId zone;

    MutableClock(Instant instant, ZoneId zone) {
      this.instant = instant;
      this.zone = zone;
    }

    void setInstant(Instant instant) {
      this.instant = instant;
    }

    @Override
    public ZoneId getZone() {
      return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return new MutableClock(instant, zone);
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }
}
