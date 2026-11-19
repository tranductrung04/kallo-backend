package com.healthcare.kallo.model.nutri;

import com.healthcare.kallo.model.auth.User;
import com.healthcare.kallo.model.food.Food;
import com.healthcare.kallo.model.food.enums.MealType;
import com.healthcare.kallo.model.nutri.enums.FoodLogSource;
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
    name = "user_food_logs",
    uniqueConstraints = {
      @UniqueConstraint(name = "uq_user_food_log_client_uuid", columnNames = {"user_id", "client_uuid"})
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class UserFoodLog {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "food_id", nullable = false)
  private Food food;

  @Enumerated(EnumType.STRING)
  @Column(name = "meal_type", nullable = false, length = 20)
  private MealType mealType;

  @Column(name = "serving_name", nullable = false, length = 100)
  private String servingName;

  @Column(name = "serving_grams", nullable = false, precision = 6, scale = 1)
  private BigDecimal servingGrams;

  @Column(name = "serving_quantity", nullable = false, precision = 4, scale = 2)
  private BigDecimal servingQuantity = BigDecimal.ONE;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private FoodLogSource source = FoodLogSource.SEARCH;

  @Column(name = "logged_date", nullable = false)
  private LocalDate loggedDate;

  @Column(name = "logged_at", nullable = false)
  private Instant loggedAt;

  @Column(name = "client_uuid", nullable = false)
  private UUID clientUuid;

  @Column(name = "image_url", columnDefinition = "TEXT")
  private String imageUrl;

  // --- Nutrition Snapshot ---
  @Column(nullable = false, precision = 6, scale = 1)
  private BigDecimal calories;

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

  // --- AI Metadata ---
  @Column(name = "ai_predicted_class_id")
  private Integer aiPredictedClassId;

  @Column(name = "feedback_given", nullable = false)
  private boolean feedbackGiven = false;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof UserFoodLog that)) return false;
    return id != null && id.equals(that.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
