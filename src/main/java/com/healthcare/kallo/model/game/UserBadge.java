package com.healthcare.kallo.model.game;

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
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "user_badges")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class UserBadge {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "badge_id", nullable = false)
  private Badge badge;

  @CreatedDate
  @Column(name = "earned_at", nullable = false, updatable = false)
  private Instant earnedAt;

  @Override public boolean equals(Object other) { return this == other || (other instanceof UserBadge that && id != null && id.equals(that.id)); }
  @Override public int hashCode() { return getClass().hashCode(); }
}
