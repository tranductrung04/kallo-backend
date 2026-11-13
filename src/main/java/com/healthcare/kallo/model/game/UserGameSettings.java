package com.healthcare.kallo.model.game;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import jakarta.persistence.EntityListeners;

@Entity
@Table(name = "user_game_settings")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class UserGameSettings {
  @Id
  @Column(name = "user_id")
  private UUID userId;

  @Column(name = "friend_code", nullable = false, length = 12)
  private String friendCode;

  @Column(nullable = false)
  private boolean discoverable = true;

  @Column(name = "share_streak", nullable = false)
  private boolean shareStreak = true;

  @Column(name = "share_points", nullable = false)
  private boolean sharePoints = true;

  @Column(name = "share_steps", nullable = false)
  private boolean shareSteps;

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(nullable = false)
  private Instant updatedAt;

  @Override
  public boolean equals(Object other) {
    return this == other || (other instanceof UserGameSettings that && userId != null && userId.equals(that.userId));
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
