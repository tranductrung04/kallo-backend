package com.healthcare.kallo.model.report;

import com.healthcare.kallo.model.auth.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(
    name = "daily_summaries",
    uniqueConstraints = {
      @UniqueConstraint(name = "uq_daily_summary_user_date", columnNames = {"user_id", "summary_date"})
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class DailySummary {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "summary_date", nullable = false)
  private LocalDate summaryDate;

  // Dinh dưỡng nạp vào (Calories In)
  @Column(name = "calories_consumed", nullable = false)
  private int caloriesConsumed = 0;

  @Column(name = "target_calories", nullable = false)
  private int targetCalories = 2000;

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

  // Nước uống
  @Column(name = "water_ml", nullable = false)
  private int waterMl = 0;

  @Column(name = "target_water_ml", nullable = false)
  private int targetWaterMl = 2000;

  // Vận động & Tiêu hao (Calories Out)
  @Column(name = "calories_burned", nullable = false)
  private int caloriesBurned = 0;

  @Column(nullable = false)
  private int steps = 0;

  @Column(name = "weight_kg", precision = 5, scale = 1)
  private BigDecimal weightKg;

  // Trạng thái mục tiêu
  @Column(name = "is_goal_achieved", nullable = false)
  private boolean goalAchieved = false;

  @Column(name = "is_sugar_exceeded", nullable = false)
  private boolean sugarExceeded = false;

  @Column(name = "is_protein_exceeded", nullable = false)
  private boolean proteinExceeded = false;

  @Column(name = "meals_logged_count", nullable = false)
  private int mealsLoggedCount = 0;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof DailySummary that)) return false;
    return id != null && id.equals(that.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
