package com.healthcare.kallo.model.nutri;

import com.healthcare.kallo.model.food.enums.MealType;
import com.healthcare.kallo.model.nutri.enums.LogSource;
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
    name = "user_food_logs",
    uniqueConstraints = @UniqueConstraint(name = "uq_user_food_log_client_uuid", columnNames = {"user_id", "client_uuid"}))
@Getter
@Setter
@NoArgsConstructor
public class UserFoodLog {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "food_id")
  private UUID foodId;

  @Enumerated(EnumType.STRING)
  @Column(name = "meal_type", length = 20)
  private MealType mealType;

  @Column(name = "serving_name", length = 100)
  private String servingName;

  @Column(name = "serving_grams", precision = 6, scale = 1)
  private BigDecimal servingGrams;

  @Column(name = "serving_quantity", nullable = false, precision = 4, scale = 2)
  private BigDecimal servingQuantity = BigDecimal.ONE;

  @Enumerated(EnumType.STRING)
  @Column(name = "source", nullable = false, length = 30)
  private LogSource source = LogSource.SEARCH;

  @Column(name = "logged_date")
  private LocalDate loggedDate;

  @Column(name = "logged_at", nullable = false)
  private Instant loggedAt;

  @Column(name = "image_key", columnDefinition = "TEXT")
  private String imageKey;

  @Column(name = "calories", precision = 6, scale = 1)
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

  @Column(name = "ai_predicted_class_id")
  private Integer aiPredictedClassId;

  @Column(name = "feedback_given", nullable = false)
  private boolean feedbackGiven = false;

  @Column(name = "user_consented_feedback", nullable = false)
  private boolean userConsentedFeedback = false;

  @Column(name = "client_uuid", nullable = false)
  private UUID clientUuid;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @Column(name = "sync_seq", nullable = false, insertable = false, updatable = false)
  private Long syncSeq;

  @Column(name = "server_updated_at", nullable = false, insertable = false, updatable = false)
  private Instant serverUpdatedAt;

  @PrePersist
  void prePersist() {
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    if (updatedAt == null) updatedAt = now;
    if (loggedAt == null) loggedAt = now;
  }
}
