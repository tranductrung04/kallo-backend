package com.healthcare.kallo.model.chat;

import com.healthcare.kallo.model.auth.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@DynamicUpdate
@Table(name = "conversations")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class Conversation {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "initial_client_message_id")
  private UUID initialClientMessageId;

  @Column(length = 120)
  private String title;

  @Column(columnDefinition = "TEXT")
  private String summary;

  @Column(name = "message_count", nullable = false)
  private int messageCount;

  @Column(name = "last_message_at")
  private Instant lastMessageAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void onCreate() {
    if (createdAt == null) createdAt = Instant.now();
    if (updatedAt == null) updatedAt = createdAt;
  }

  @Override
  public boolean equals(Object other) {
    return this == other || (other instanceof Conversation that && id != null && id.equals(that.id));
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
