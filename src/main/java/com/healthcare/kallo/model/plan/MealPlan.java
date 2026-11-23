package com.healthcare.kallo.model.plan;

import com.healthcare.kallo.model.auth.User;
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
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "meal_plans")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class MealPlan {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(
      name = "user_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_meal_plans_user"))
  private User user;

  @Column(nullable = false, length = 150)
  private String name;

  @Column(columnDefinition = "text")
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private MealPlanType type = MealPlanType.DAILY;

  @Column(name = "plan_date", nullable = false)
  private LocalDate planDate;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private MealPlanStatus status = MealPlanStatus.DRAFT;

  @Column(name = "target_calories", nullable = false)
  private int targetCalories;

  @Column(name = "total_calories", nullable = false)
  private int totalCalories = 0;

  @Column(name = "total_protein_g", nullable = false, precision = 6, scale = 1)
  private BigDecimal totalProteinG = BigDecimal.ZERO;

  @Column(name = "total_carbs_g", nullable = false, precision = 6, scale = 1)
  private BigDecimal totalCarbsG = BigDecimal.ZERO;

  @Column(name = "total_fat_g", nullable = false, precision = 6, scale = 1)
  private BigDecimal totalFatG = BigDecimal.ZERO;

  @Column(name = "total_sugar_g", nullable = false, precision = 6, scale = 1)
  private BigDecimal totalSugarG = BigDecimal.ZERO;

  @Column(name = "total_fiber_g", nullable = false, precision = 6, scale = 1)
  private BigDecimal totalFiberG = BigDecimal.ZERO;

  @Column(name = "estimated_cost_vnd", nullable = false)
  private int estimatedCostVnd = 0;

  @Column(name = "max_budget_vnd")
  private Integer maxBudgetVnd;

  @Column(name = "preference_match_score", nullable = false, precision = 4, scale = 2)
  private BigDecimal preferenceMatchScore = BigDecimal.ZERO;

  @Column(name = "medical_warning", columnDefinition = "text")
  private String medicalWarning;

  @Column(name = "applied_at")
  private Instant appliedAt;

  @Column(name = "applied_date")
  private LocalDate appliedDate;

  @OneToMany(mappedBy = "mealPlan", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<MealPlanFood> items = new ArrayList<>();

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public void addItem(MealPlanFood item) {
    items.add(item);
    item.setMealPlan(this);
  }

  public void removeItem(MealPlanFood item) {
    items.remove(item);
    item.setMealPlan(null);
  }
}
