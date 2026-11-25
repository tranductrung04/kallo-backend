package com.healthcare.kallo.service.food;

import com.healthcare.kallo.model.food.Food;
import com.healthcare.kallo.model.food.FoodIngredient;
import com.healthcare.kallo.model.food.Ingredient;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class FoodNutritionCalculator {

  public static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

  public void recalculateNutritionSnapshot(Food food, List<FoodIngredient> items) {
    if (items == null || items.isEmpty()) {
      return;
    }

    BigDecimal totalGrams = BigDecimal.ZERO;
    BigDecimal totalCalories = BigDecimal.ZERO;
    BigDecimal totalProtein = BigDecimal.ZERO;
    BigDecimal totalCarbs = BigDecimal.ZERO;
    BigDecimal totalFat = BigDecimal.ZERO;
    BigDecimal totalSugar = BigDecimal.ZERO;
    BigDecimal totalFiber = BigDecimal.ZERO;

    for (FoodIngredient item : items) {
      BigDecimal qty = item.getQuantityGrams() != null ? item.getQuantityGrams() : BigDecimal.ZERO;
      totalGrams = totalGrams.add(qty);

      Ingredient ing = item.getIngredient();
      if (ing != null) {
        BigDecimal ratio = qty.divide(HUNDRED, 4, RoundingMode.HALF_UP);
        totalCalories = totalCalories.add(ing.getCaloriesPer100g().multiply(ratio));
        totalProtein = totalProtein.add(ing.getProteinPer100g().multiply(ratio));
        totalCarbs = totalCarbs.add(ing.getCarbsPer100g().multiply(ratio));
        totalFat = totalFat.add(ing.getFatPer100g().multiply(ratio));
        totalSugar = totalSugar.add(ing.getSugarPer100g().multiply(ratio));
        totalFiber = totalFiber.add(ing.getFiberPer100g().multiply(ratio));
      }
    }

    if (totalGrams.compareTo(BigDecimal.ZERO) <= 0) {
      return;
    }

    // Default serving grams defaults to total grams if not set or <= 0
    if (food.getDefaultServingGrams() == null || food.getDefaultServingGrams().compareTo(BigDecimal.ZERO) <= 0) {
      food.setDefaultServingGrams(totalGrams.setScale(1, RoundingMode.HALF_UP));
    }

    // Nutrients per 100g of food
    BigDecimal factor100 = HUNDRED.divide(totalGrams, 4, RoundingMode.HALF_UP);
    food.setCaloriesPer100g(totalCalories.multiply(factor100).setScale(1, RoundingMode.HALF_UP));
    food.setProteinPer100g(totalProtein.multiply(factor100).setScale(1, RoundingMode.HALF_UP));
    food.setCarbsPer100g(totalCarbs.multiply(factor100).setScale(1, RoundingMode.HALF_UP));
    food.setFatPer100g(totalFat.multiply(factor100).setScale(1, RoundingMode.HALF_UP));
    food.setSugarPer100g(totalSugar.multiply(factor100).setScale(1, RoundingMode.HALF_UP));
    food.setFiberPer100g(totalFiber.multiply(factor100).setScale(1, RoundingMode.HALF_UP));

    // Nutrients per serving
    BigDecimal servingFactor = food.getDefaultServingGrams().divide(HUNDRED, 4, RoundingMode.HALF_UP);
    food.setCaloriesPerServing(food.getCaloriesPer100g().multiply(servingFactor).setScale(0, RoundingMode.HALF_UP).intValue());
    food.setProteinPerServing(food.getProteinPer100g().multiply(servingFactor).setScale(1, RoundingMode.HALF_UP));
    food.setCarbsPerServing(food.getCarbsPer100g().multiply(servingFactor).setScale(1, RoundingMode.HALF_UP));
    food.setFatPerServing(food.getFatPer100g().multiply(servingFactor).setScale(1, RoundingMode.HALF_UP));
    food.setSugarPerServing(food.getSugarPer100g().multiply(servingFactor).setScale(1, RoundingMode.HALF_UP));
    food.setFiberPerServing(food.getFiberPer100g().multiply(servingFactor).setScale(1, RoundingMode.HALF_UP));
  }
}
