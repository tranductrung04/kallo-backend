package com.healthcare.kallo.model.plan;

import com.healthcare.kallo.model.food.Food;
import com.healthcare.kallo.model.food.enums.MealType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "meal_plan_foods")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class MealPlanFood {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(
      name = "meal_plan_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_meal_plan_foods_plan"))
  private MealPlan mealPlan;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(
      name = "food_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_meal_plan_foods_food"))
  private Food food;

  @Enumerated(EnumType.STRING)
  @Column(name = "meal_type", nullable = false, length = 20)
  private MealType mealType;

  @Column(name = "serving_name", nullable = false, length = 100)
  private String servingName;

  @Column(name = "serving_grams", nullable = false, precision = 6, scale = 1)
  private BigDecimal servingGrams;

  @Column(name = "serving_multiplier", nullable = false, precision = 4, scale = 2)
  private BigDecimal servingMultiplier = BigDecimal.ONE;

  @Column(nullable = false)
  private int calories;

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
  private int estimatedPriceVnd = 0;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
