package com.healthcare.kallo.model.food;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "barcode_misses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BarcodeMiss {

  @Id
  @Column(length = 64, nullable = false)
  private String barcode;

  @Column(name = "queried_at", nullable = false)
  private Instant queriedAt = Instant.now();

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  public BarcodeMiss(String barcode, Instant expiresAt) {
    this.barcode = barcode;
    this.queriedAt = Instant.now();
    this.expiresAt = expiresAt;
  }
}
