package com.healthcare.kallo.model.plan;

import com.healthcare.kallo.model.plan.enums.MealPlanStatus;
import com.healthcare.kallo.model.plan.enums.MealPlanType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "meal_plans")
@Getter
@Setter
@NoArgsConstructor
public class MealPlan {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "name", nullable = false, length = 150)
  private String name;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, length = 30)
  private MealPlanType type = MealPlanType.DAILY;

  @Column(name = "plan_date", nullable = false)
  private LocalDate planDate;

  @Column(name = "days_count", nullable = false)
  private Integer daysCount = 1;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private MealPlanStatus status = MealPlanStatus.DRAFT;

  @Column(name = "target_calories", nullable = false)
  private Integer targetCalories;

  @Column(name = "total_calories", nullable = false)
  private Integer totalCalories = 0;

  @Column(name = "total_protein_g", nullable = false, precision = 7, scale = 1)
  private BigDecimal totalProteinG = BigDecimal.ZERO;

  @Column(name = "total_carbs_g", nullable = false, precision = 7, scale = 1)
  private BigDecimal totalCarbsG = BigDecimal.ZERO;

  @Column(name = "total_fat_g", nullable = false, precision = 7, scale = 1)
  private BigDecimal totalFatG = BigDecimal.ZERO;

  @Column(name = "total_sugar_g", nullable = false, precision = 7, scale = 1)
  private BigDecimal totalSugarG = BigDecimal.ZERO;

  @Column(name = "total_fiber_g", nullable = false, precision = 7, scale = 1)
  private BigDecimal totalFiberG = BigDecimal.ZERO;

  @Column(name = "estimated_cost_vnd", nullable = false)
  private Integer estimatedCostVnd = 0;

  @Column(name = "max_budget_vnd")
  private Integer maxBudgetVnd;

  @Column(name = "preference_match_score", nullable = false, precision = 4, scale = 2)
  private BigDecimal preferenceMatchScore = BigDecimal.ZERO;

  @Column(name = "medical_warning", columnDefinition = "TEXT")
  private String medicalWarning;

  @Column(name = "applied_at")
  private Instant appliedAt;

  @Column(name = "applied_date")
  private LocalDate appliedDate;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @OneToMany(mappedBy = "mealPlan", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("dayIndex ASC, mealType ASC, createdAt ASC")
  private List<MealPlanFood> items = new ArrayList<>();

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
