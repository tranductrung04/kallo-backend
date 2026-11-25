package com.healthcare.kallo.model.food;

import com.healthcare.kallo.model.food.enums.FoodSource;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "ingredients")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Ingredient {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "code", length = 50)
  private String code;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "name_unaccented", insertable = false, updatable = false)
  private String nameUnaccented;

  @Column(name = "calories_per_100g", nullable = false, precision = 6, scale = 1)
  private BigDecimal caloriesPer100g = BigDecimal.ZERO;

  @Column(name = "protein_per_100g", nullable = false, precision = 5, scale = 1)
  private BigDecimal proteinPer100g = BigDecimal.ZERO;

  @Column(name = "carbs_per_100g", nullable = false, precision = 5, scale = 1)
  private BigDecimal carbsPer100g = BigDecimal.ZERO;

  @Column(name = "fat_per_100g", nullable = false, precision = 5, scale = 1)
  private BigDecimal fatPer100g = BigDecimal.ZERO;

  @Column(name = "sugar_per_100g", nullable = false, precision = 5, scale = 1)
  private BigDecimal sugarPer100g = BigDecimal.ZERO;

  @Column(name = "fiber_per_100g", nullable = false, precision = 5, scale = 1)
  private BigDecimal fiberPer100g = BigDecimal.ZERO;

  @Column(name = "is_allergen", nullable = false)
  private boolean isAllergen = false;

  @Enumerated(EnumType.STRING)
  @Column(name = "source", nullable = false, length = 30)
  private FoodSource source = FoodSource.SYSTEM;

  @Column(name = "owner_user_id")
  private UUID ownerUserId;

  @Column(name = "is_active", nullable = false)
  private boolean isActive = true;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
