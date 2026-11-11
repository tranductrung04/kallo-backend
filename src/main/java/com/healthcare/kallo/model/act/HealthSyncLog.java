package com.healthcare.kallo.model.act;

import com.healthcare.kallo.model.act.enums.HealthDataType;
import com.healthcare.kallo.model.act.enums.HealthProvider;
import com.healthcare.kallo.model.auth.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "health_sync_logs")
@Getter
@Setter
public class HealthSyncLog {
  @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
  @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false) private User user;
  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private HealthProvider provider;
  @Enumerated(EnumType.STRING) @Column(name = "data_type", nullable = false, length = 20)
  private HealthDataType dataType;
  @Column(name = "external_id", nullable = false, length = 100) private String externalId;
  @Column(nullable = false, precision = 12, scale = 2) private BigDecimal value;
  @Column(name = "value_min", precision = 12, scale = 2) private BigDecimal valueMin;
  @Column(name = "value_max", precision = 12, scale = 2) private BigDecimal valueMax;
  @Column(name = "data_origin", length = 100) private String dataOrigin;
  @Column(name = "start_time", nullable = false) private Instant startTime;
  @Column(name = "end_time", nullable = false) private Instant endTime;
  @Column(name = "synced_at", nullable = false) private Instant syncedAt;
}
