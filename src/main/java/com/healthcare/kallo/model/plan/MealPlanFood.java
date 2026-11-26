package com.healthcare.kallo.model.plan;

import com.healthcare.kallo.model.food.enums.MealType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "meal_plan_foods")
@Getter
@Setter
@NoArgsConstructor
public class MealPlanFood {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "meal_plan_id", nullable = false)
  private MealPlan mealPlan;

  @Column(name = "food_id", nullable = false)
  private UUID foodId;

  @Column(name = "day_index", nullable = false)
  private Integer dayIndex = 1;

  @Column(name = "meal_date", nullable = false)
  private LocalDate mealDate;

  @Enumerated(EnumType.STRING)
  @Column(name = "meal_type", nullable = false, length = 20)
  private MealType mealType;

  @Column(name = "serving_name", nullable = false, length = 100)
  private String servingName;

  @Column(name = "serving_grams", nullable = false, precision = 6, scale = 1)
  private BigDecimal servingGrams;

  @Column(name = "serving_multiplier", nullable = false, precision = 4, scale = 2)
  private BigDecimal servingMultiplier = BigDecimal.ONE;

  @Column(name = "calories", nullable = false)
  private Integer calories = 0;

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

  @Column(name = "estimated_price_vnd", nullable = false)
  private Integer estimatedPriceVnd = 0;

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
