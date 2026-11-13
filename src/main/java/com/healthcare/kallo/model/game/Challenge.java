package com.healthcare.kallo.model.game;

import com.healthcare.kallo.model.game.enums.ChallengeMetric;
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
@Table(name = "challenges")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class Challenge {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = 150)
  private String title;

  @Column(nullable = false, length = 1000)
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private ChallengeMetric metric;

  @Column(name = "target_value", nullable = false)
  private int targetValue;

  @Column(name = "start_date", nullable = false)
  private LocalDate startDate;

  @Column(name = "end_date", nullable = false)
  private LocalDate endDate;

  @Column(name = "points_reward", nullable = false)
  private int pointsReward;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "badge_id")
  private Badge badge;

  @Column(nullable = false)
  private boolean active = true;

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(nullable = false)
  private Instant updatedAt;

  @Override public boolean equals(Object other) { return this == other || (other instanceof Challenge that && id != null && id.equals(that.id)); }
  @Override public int hashCode() { return getClass().hashCode(); }
}
