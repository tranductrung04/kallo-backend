package com.healthcare.kallo.model.game;

import com.healthcare.kallo.model.auth.User;
import com.healthcare.kallo.model.game.enums.ChallengeStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "user_challenges")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class UserChallenge {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "challenge_id", nullable = false)
  private Challenge challenge;

  @Column(nullable = false)
  private int progress;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ChallengeStatus status = ChallengeStatus.ACTIVE;

  @CreatedDate
  @Column(name = "joined_at", nullable = false, updatable = false)
  private Instant joinedAt;

  @Column(name = "joined_local_date", nullable = false)
  private LocalDate joinedLocalDate;

  @Column(name = "completed_at")
  private Instant completedAt;

  @LastModifiedDate
  @Column(nullable = false)
  private Instant updatedAt;

  @Override public boolean equals(Object other) { return this == other || (other instanceof UserChallenge that && id != null && id.equals(that.id)); }
  @Override public int hashCode() { return getClass().hashCode(); }
}
