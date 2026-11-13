package com.healthcare.kallo.model.game;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "streaks")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class Streak {
  @Id
  @Column(name = "user_id")
  private UUID userId;

  @Column(name = "current_streak", nullable = false)
  private int currentStreak;

  @Column(name = "longest_streak", nullable = false)
  private int longestStreak;

  @Column(name = "last_completed_date")
  private LocalDate lastCompletedDate;

  @LastModifiedDate
  @Column(nullable = false)
  private Instant updatedAt;

  @Override
  public boolean equals(Object other) {
    return this == other || (other instanceof Streak that && userId != null && userId.equals(that.userId));
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
