package com.healthcare.kallo.model.game;

import com.healthcare.kallo.model.auth.User;
import com.healthcare.kallo.model.game.enums.PointSource;
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
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "point_transactions")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class PointTransaction {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(nullable = false)
  private int points;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_type", nullable = false, length = 30)
  private PointSource sourceType;

  @Column(name = "source_id", nullable = false)
  private UUID sourceId;

  @CreatedDate
  @Column(name = "occurred_at", nullable = false, updatable = false)
  private Instant occurredAt;

  @Override public boolean equals(Object other) { return this == other || (other instanceof PointTransaction that && id != null && id.equals(that.id)); }
  @Override public int hashCode() { return getClass().hashCode(); }
}
