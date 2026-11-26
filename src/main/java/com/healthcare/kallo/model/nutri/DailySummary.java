package com.healthcare.kallo.model.nutri;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "daily_summaries",
    uniqueConstraints = @UniqueConstraint(name = "uq_daily_summary_user_date", columnNames = {"user_id", "summary_date"}))
@Getter
@Setter
@NoArgsConstructor
public class DailySummary {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "summary_date", nullable = false)
  private LocalDate summaryDate;

  @Column(name = "calories_consumed", nullable = false)
  private Integer caloriesConsumed = 0;

  @Column(name = "target_calories")
  private Integer targetCalories;

  @Column(name = "protein_g", nullable = false, precision = 6, scale = 1)
  private BigDecimal proteinG = BigDecimal.ZERO;

  @Column(name = "carbs_g", nullable = false, precision = 6, scale = 1)
  private BigDecimal carbsG = BigDecimal.ZERO;

  @Column(name = "fat_g", nullable = false, precision = 6, scale = 1)
  private BigDecimal fatG = BigDecimal.ZERO;

  @Column(name = "sugar_g", nullable = false, precision = 6, scale = 1)
  private BigDecimal sugarG = BigDecimal.ZERO;

  @Column(name = "fiber_g", nullable = false, precision = 6, scale = 1)
  private BigDecimal fiberG = BigDecimal.ZERO;

  @Column(name = "water_ml", nullable = false)
  private Integer waterMl = 0;

  @Column(name = "target_water_ml")
  private Integer targetWaterMl;

  @Column(name = "calories_burned", nullable = false)
  private Integer caloriesBurned = 0;

  @Column(name = "steps", nullable = false)
  private Integer steps = 0;

  @Column(name = "weight_kg", precision = 5, scale = 1)
  private BigDecimal weightKg;

  @Column(name = "is_goal_achieved", nullable = false)
  private boolean isGoalAchieved = false;

  @Column(name = "is_sugar_exceeded", nullable = false)
  private boolean isSugarExceeded = false;

  @Column(name = "is_protein_exceeded", nullable = false)
  private boolean isProteinExceeded = false;

  @Column(name = "meals_logged_count", nullable = false)
  private int mealsLoggedCount = 0;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void prePersist() {
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    if (updatedAt == null) updatedAt = now;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }
}
