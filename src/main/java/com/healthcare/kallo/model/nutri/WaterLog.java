package com.healthcare.kallo.model.nutri;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "water_logs",
    uniqueConstraints = @UniqueConstraint(name = "uq_water_log_client_uuid", columnNames = {"user_id", "client_uuid"}))
@Getter
@Setter
@NoArgsConstructor
public class WaterLog {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "amount_ml")
  private Integer amountMl;

  @Column(name = "logged_date")
  private LocalDate loggedDate;

  @Column(name = "logged_at", nullable = false)
  private Instant loggedAt;

  @Column(name = "note", length = 255)
  private String note;

  @Column(name = "client_uuid", nullable = false)
  private UUID clientUuid;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @Column(name = "sync_seq", nullable = false, insertable = false, updatable = false)
  private Long syncSeq;

  @Column(name = "server_updated_at", nullable = false, insertable = false, updatable = false)
  private Instant serverUpdatedAt;

  @PrePersist
  void prePersist() {
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    if (updatedAt == null) updatedAt = now;
    if (loggedAt == null) loggedAt = now;
  }
}
