package com.healthcare.kallo.model.act;

import com.healthcare.kallo.model.act.enums.ActivitySource;
import com.healthcare.kallo.model.act.enums.Intensity;
import com.healthcare.kallo.model.auth.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "activity_logs")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class ActivityLog {
  @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
  @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false) private User user;
  @Column(name = "client_uuid", nullable = false) private UUID clientUuid;
  @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "activity_type", nullable = false)
  private ActivityType activityType;
  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private Intensity intensity;
  @Column(nullable = false, precision = 4, scale = 1) private BigDecimal met;
  @Column(name = "weight_kg_used", nullable = false, precision = 5, scale = 1) private BigDecimal weightKgUsed;
  @Column(name = "duration_min", nullable = false) private int durationMin;
  @Column(name = "distance_km", precision = 6, scale = 2) private BigDecimal distanceKm;
  @Column(name = "calories_burned", nullable = false, precision = 7, scale = 1) private BigDecimal caloriesBurned;
  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ActivitySource source;
  @Column(name = "external_id", length = 100) private String externalId;
  @Column(name = "started_at", nullable = false) private Instant startedAt;
  @Column(name = "ended_at", nullable = false) private Instant endedAt;
  @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "superseded_by") private ActivityLog supersededBy;
  @Column(length = 255) private String note;
  @CreatedDate @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
  @LastModifiedDate @Column(name = "updated_at", nullable = false) private Instant updatedAt;
  @Column(name = "deleted_at") private Instant deletedAt;

  @Override public boolean equals(Object other) { return this == other || (other instanceof ActivityLog that && id != null && id.equals(that.id)); }
  @Override public int hashCode() { return getClass().hashCode(); }
}
