package com.healthcare.kallo.service.sys.impl;

import com.healthcare.kallo.event.sys.NotificationCreatedEvent;
import com.healthcare.kallo.model.sys.Notification;
import com.healthcare.kallo.repository.sys.NotificationRepository;
import com.healthcare.kallo.service.sys.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import java.time.Instant;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationServiceImpl implements NotificationService {
  private final NotificationRepository notifications;
  private final EntityManager entityManager;
  private final ApplicationEventPublisher events;
  private final ObjectMapper objectMapper;
  private final NotificationMessageFactory messageFactory;
  private final Clock clock;
  private final NotificationPolicy policy;

  public NotificationServiceImpl(
      NotificationRepository notifications,
      EntityManager entityManager,
      ApplicationEventPublisher events,
      ObjectMapper objectMapper,
      NotificationMessageFactory messageFactory,
      Clock clock,
      NotificationPolicy policy) {
    this.notifications = notifications;
    this.entityManager = entityManager;
    this.events = events;
    this.objectMapper = objectMapper;
    this.messageFactory = messageFactory;
    this.clock = clock;
    this.policy = policy;
  }

  @Override
  @Transactional
  public NotifyOutcome notify(NotifyCommand command) {
    if (command.userId() == null || command.type() == null) {
      throw new IllegalArgumentException("Notification command is incomplete");
    }
    NotifyOutcome policyOutcome = policy.evaluate(command.userId(), command.type());
    if (policyOutcome != NotifyOutcome.SENT) return policyOutcome;
    NotificationContent content = messageFactory.render(command.type(), command.params());
    validateLength(content.title(), 200, "title");
    validateLength(content.body(), 1000, "body");
    if (command.dedupeKey() != null) validateLength(command.dedupeKey(), 120, "dedupeKey");
    try {
      UUID id = (UUID) entityManager.createNativeQuery("""
          INSERT INTO notifications (user_id, type, title, body, data, dedupe_key, created_at)
          VALUES (:userId, :type, :title, :body, CAST(:data AS jsonb), :dedupeKey, :createdAt)
          ON CONFLICT (user_id, dedupe_key) WHERE dedupe_key IS NOT NULL DO NOTHING
          RETURNING id
          """)
          .setParameter("userId", command.userId())
          .setParameter("type", command.type().name())
          .setParameter("title", content.title())
          .setParameter("body", content.body())
          .setParameter("data", toJson(command))
          .setParameter("dedupeKey", command.dedupeKey())
          .setParameter("createdAt", Instant.now(clock))
          .getSingleResult();
      events.publishEvent(new NotificationCreatedEvent(
          id, command.userId(), command.type(), content.pushTitle(), content.pushBody()));
      return NotifyOutcome.SENT;
    } catch (NoResultException duplicate) {
      return NotifyOutcome.DUPLICATE;
    }
  }

  private String toJson(NotifyCommand command) {
    try {
      return objectMapper.writeValueAsString(command.data() == null ? Map.of() : command.data());
    } catch (Exception e) {
      throw new IllegalArgumentException("Notification data is not serializable", e);
    }
  }

  private void validateLength(String value, int max, String field) {
    if (value == null || value.length() > max) {
      throw new IllegalArgumentException(field + " exceeds " + max + " characters");
    }
  }
}
