package com.healthcare.kallo.model.food;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "food_ingredients")
@Getter
@Setter
@NoArgsConstructor
public class FoodIngredient {

  @EmbeddedId
  private FoodIngredientId id = new FoodIngredientId();

  @ManyToOne(fetch = FetchType.LAZY)
  @MapsId("foodId")
  @JoinColumn(name = "food_id", nullable = false)
  private Food food;

  @ManyToOne(fetch = FetchType.LAZY)
  @MapsId("ingredientId")
  @JoinColumn(name = "ingredient_id", nullable = false)
  private Ingredient ingredient;

  @Column(name = "quantity_grams", precision = 7, scale = 1)
  private BigDecimal quantityGrams;

  public FoodIngredient(Food food, Ingredient ingredient, BigDecimal quantityGrams) {
    this.food = food;
    this.ingredient = ingredient;
    this.id = new FoodIngredientId(food != null ? food.getId() : null, ingredient != null ? ingredient.getId() : null);
    this.quantityGrams = quantityGrams;
  }
}
