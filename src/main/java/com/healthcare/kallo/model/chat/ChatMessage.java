package com.healthcare.kallo.model.chat;

import com.healthcare.kallo.model.chat.enums.ChatMessageStatus;
import com.healthcare.kallo.model.chat.enums.ChatRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import jakarta.persistence.PrePersist;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "chat_messages")
@Getter
@Setter
public class ChatMessage {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "conversation_id", nullable = false)
  private Conversation conversation;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private ChatRole role;

  @Column(columnDefinition = "TEXT")
  private String content;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private ChatMessageStatus status = ChatMessageStatus.COMPLETED;

  @Column(name = "client_message_id")
  private UUID clientMessageId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parent_message_id")
  private ChatMessage parentMessage;

  @Column(name = "prompt_tokens")
  private Integer promptTokens;

  @Column(name = "completion_tokens")
  private Integer completionTokens;

  @Column(name = "model_version", length = 64)
  private String modelVersion;

  @Column(name = "latency_ms")
  private Integer latencyMs;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private Map<String, Object> metadata;

  private Short feedback;

  @Column(name = "pending_since")
  private Instant pendingSince;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @PrePersist
  void onCreate() {
    if (createdAt == null) createdAt = Instant.now();
    if (status == ChatMessageStatus.PENDING && pendingSince == null) pendingSince = createdAt;
  }

  @Override
  public boolean equals(Object other) {
    return this == other || (other instanceof ChatMessage that && id != null && id.equals(that.id));
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
