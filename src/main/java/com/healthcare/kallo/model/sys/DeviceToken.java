package com.healthcare.kallo.model.sys;

import com.healthcare.kallo.model.sys.enums.DevicePlatform;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "device_tokens")
@Getter
@Setter
public class DeviceToken {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(nullable = false, length = 512)
  private String token;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private DevicePlatform platform;

  @Column(length = 10)
  private String locale;

  @Column(name = "app_version", length = 20)
  private String appVersion;

  @Column(name = "last_seen_at", nullable = false)
  private Instant lastSeenAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    if (lastSeenAt == null) lastSeenAt = now;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof DeviceToken device)) return false;
    return id != null && id.equals(device.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
