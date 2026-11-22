package com.healthcare.kallo.model.auth;

import com.healthcare.kallo.model.auth.enums.AuthProvider;
import com.healthcare.kallo.model.auth.enums.Role;
import com.healthcare.kallo.model.auth.enums.UserStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "users")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class User {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private String email;

  @Column(name = "email_verified", nullable = false)
  private boolean emailVerified;

  private String passwordHash;
  @Column(length = 255)
  private String googleSub;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AuthProvider authProvider;

  @Column(nullable = false)
  private String displayName;

  private String avatarUrl;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Role role = Role.USER;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private UserStatus status = UserStatus.ACTIVE;

  @Column(nullable = false)
  private String timezone = "Asia/Ho_Chi_Minh";

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private List<String> allergies = List.of();

  @Column(name = "height_cm", precision = 5, scale = 1)
  private java.math.BigDecimal heightCm;

  @Column(name = "weight_kg", precision = 5, scale = 1)
  private java.math.BigDecimal weightKg;

  @Column(length = 20)
  private String gender;

  @Column(name = "birth_date")
  private LocalDate birthDate;

  @Column(name = "target_weight_kg", precision = 5, scale = 1)
  private java.math.BigDecimal targetWeightKg;

  @Column(name = "step_goal")
  private Integer stepGoal;

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(nullable = false)
  private Instant updatedAt;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof User other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
