package com.healthcare.kallo.model.profile;

import com.healthcare.kallo.model.profile.enums.ActivityLevel;
import com.healthcare.kallo.model.profile.enums.Gender;
import com.healthcare.kallo.model.profile.enums.GoalType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "user_health_profiles")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class UserHealthProfile implements Persistable<UUID> {

  @Id
  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "gender", nullable = false, length = 10)
  private Gender gender;

  @Column(name = "dob", nullable = false)
  private LocalDate dob;

  @Column(name = "height_cm", nullable = false, precision = 5, scale = 2)
  private BigDecimal heightCm;

  @Column(name = "weight_kg", nullable = false, precision = 5, scale = 2)
  private BigDecimal weightKg;

  @Enumerated(EnumType.STRING)
  @Column(name = "activity_level", nullable = false, length = 20)
  private ActivityLevel activityLevel;

  @Enumerated(EnumType.STRING)
  @Column(name = "goal_type", nullable = false, length = 20)
  private GoalType goalType;

  @Column(name = "target_weight_kg", precision = 5, scale = 2)
  private BigDecimal targetWeightKg;

  @Column(name = "weekly_rate_kg", nullable = false, precision = 3, scale = 2)
  private BigDecimal weeklyRateKg = BigDecimal.ZERO;

  @Column(name = "bmr", nullable = false, precision = 6, scale = 2)
  private BigDecimal bmr;

  @Column(name = "tdee", nullable = false, precision = 6, scale = 2)
  private BigDecimal tdee;

  @Column(name = "daily_calorie_goal", nullable = false, precision = 6, scale = 2)
  private BigDecimal dailyCalorieGoal;

  @Column(name = "carb_grams", nullable = false, precision = 6, scale = 2)
  private BigDecimal carbGrams;

  @Column(name = "protein_grams", nullable = false, precision = 6, scale = 2)
  private BigDecimal proteinGrams;

  @Column(name = "fat_grams", nullable = false, precision = 6, scale = 2)
  private BigDecimal fatGrams;

  @Column(name = "water_liters", nullable = false, precision = 4, scale = 2)
  private BigDecimal waterLiters;

  @Column(name = "step_goal")
  private Integer stepGoal;

  @Column(name = "recalculation_recommended", nullable = false)
  private boolean recalculationRecommended = false;

  @Column(name = "recommended_tdee", precision = 6, scale = 2)
  private BigDecimal recommendedTdee;

  @Column(name = "recommended_daily_calorie_goal", precision = 6, scale = 2)
  private BigDecimal recommendedDailyCalorieGoal;

  @Column(name = "latest_logged_weight_kg", precision = 5, scale = 2)
  private BigDecimal latestLoggedWeightKg;

  @Column(name = "rate_clamped", nullable = false)
  private boolean rateClamped = false;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "preference_vector", columnDefinition = "jsonb", nullable = false)
  private List<Double> preferenceVector =
      new ArrayList<>(List.of(0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5));

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Transient
  private boolean isNew = true;

  @Override
  public UUID getId() {
    return userId;
  }

  @Override
  public boolean isNew() {
    return isNew || version == null;
  }

  @PostLoad
  @PostPersist
  void markNotNew() {
    this.isNew = false;
  }
}
