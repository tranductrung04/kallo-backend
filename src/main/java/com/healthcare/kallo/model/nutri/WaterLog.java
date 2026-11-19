package com.healthcare.kallo.model.nutri;

import com.healthcare.kallo.model.auth.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(
    name = "water_logs",
    uniqueConstraints = {
      @UniqueConstraint(name = "uq_water_log_client_uuid", columnNames = {"user_id", "client_uuid"})
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class WaterLog {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "amount_ml", nullable = false)
  private int amountMl;

  @Column(name = "logged_date", nullable = false)
  private LocalDate loggedDate;

  @Column(name = "logged_at", nullable = false)
  private Instant loggedAt;

  @Column(name = "client_uuid", nullable = false)
  private UUID clientUuid;

  @Column(length = 255)
  private String note;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof WaterLog that)) return false;
    return id != null && id.equals(that.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
