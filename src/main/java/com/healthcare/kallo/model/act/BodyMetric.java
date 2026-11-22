package com.healthcare.kallo.model.act;

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
@Table(name = "body_metrics", uniqueConstraints = @UniqueConstraint(name = "uq_body_metrics_client", columnNames = {"user_id", "client_uuid"}))
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
public class BodyMetric {
  @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
  @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false) private User user;
  @Column(name = "client_uuid", nullable = false) private UUID clientUuid;
  @Column(name = "waist_cm", nullable = false, precision = 5, scale = 1) private BigDecimal waistCm;
  @Column(name = "hip_cm", nullable = false, precision = 5, scale = 1) private BigDecimal hipCm;
  @Column(name = "logged_at", nullable = false) private Instant loggedAt;
  @Column(length = 255) private String note;
  @CreatedDate @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
  @LastModifiedDate @Column(name = "updated_at", nullable = false) private Instant updatedAt;
  @Column(name = "deleted_at") private Instant deletedAt;

  @Override public boolean equals(Object other) { return this == other || (other instanceof BodyMetric that && id != null && id.equals(that.id)); }
  @Override public int hashCode() { return getClass().hashCode(); }
}
