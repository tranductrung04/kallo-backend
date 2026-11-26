package com.healthcare.kallo.model.food;

import com.healthcare.kallo.model.food.enums.FoodCategory;
import com.healthcare.kallo.model.food.enums.FoodSource;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "foods")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Food {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "name_unaccented", insertable = false, updatable = false)
  private String nameUnaccented;

  @Enumerated(EnumType.STRING)
  @Column(name = "category", nullable = false, length = 50)
  private FoodCategory category;

  @Column(name = "default_serving_name", nullable = false, length = 100)
  private String defaultServingName;

  @Column(name = "default_serving_grams", nullable = false, precision = 6, scale = 1)
  private BigDecimal defaultServingGrams;

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

  @Column(name = "calories_per_serving", nullable = false)
  private Integer caloriesPerServing = 0;

  @Column(name = "protein_per_serving", nullable = false, precision = 5, scale = 1)
  private BigDecimal proteinPerServing = BigDecimal.ZERO;

  @Column(name = "carbs_per_serving", nullable = false, precision = 5, scale = 1)
  private BigDecimal carbsPerServing = BigDecimal.ZERO;

  @Column(name = "fat_per_serving", nullable = false, precision = 5, scale = 1)
  private BigDecimal fatPerServing = BigDecimal.ZERO;

  @Column(name = "sugar_per_serving", nullable = false, precision = 5, scale = 1)
  private BigDecimal sugarPerServing = BigDecimal.ZERO;

  @Column(name = "fiber_per_serving", nullable = false, precision = 5, scale = 1)
  private BigDecimal fiberPerServing = BigDecimal.ZERO;

  @Column(name = "image_url", columnDefinition = "TEXT")
  private String imageUrl;

  @Column(name = "barcode", length = 64)
  private String barcode;

  @Enumerated(EnumType.STRING)
  @Column(name = "source", nullable = false, length = 30)
  private FoodSource source = FoodSource.SYSTEM;

  @Column(name = "vietfood_class_id")
  private Integer vietfoodClassId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "suitable_meal_types", columnDefinition = "jsonb", nullable = false)
  private List<String> suitableMealTypes = new ArrayList<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "feature_vector", columnDefinition = "jsonb")
  private List<Double> featureVector =
      new ArrayList<>(List.of(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0));

  @Column(name = "reference_price_vnd")
  private Integer referencePriceVnd;

  @Column(name = "is_verified", nullable = false)
  private boolean isVerified = true;

  @Column(name = "owner_user_id")
  private UUID ownerUserId;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @OneToMany(mappedBy = "food", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<FoodIngredient> ingredients = new ArrayList<>();
}
