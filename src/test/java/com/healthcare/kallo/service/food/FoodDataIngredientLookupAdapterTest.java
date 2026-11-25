package com.healthcare.kallo.service.food;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.healthcare.kallo.model.food.Ingredient;
import com.healthcare.kallo.model.food.enums.FoodSource;
import com.healthcare.kallo.repository.food.IngredientRepository;
import com.healthcare.kallo.service.fooddata.FoodDataIngredientLookupAdapter;
import com.healthcare.kallo.service.profile.port.IngredientSummary;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FoodDataIngredientLookupAdapterTest {

  @Mock private IngredientRepository ingredientRepository;

  private FoodDataIngredientLookupAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new FoodDataIngredientLookupAdapter(ingredientRepository);
  }

  @Test
  @DisplayName("findSummariesByIds: ánh xạ danh sách ID sang IngredientSummary thành công")
  void testFindSummariesByIds() {
    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();

    Ingredient ing1 = new Ingredient();
    ing1.setId(id1);
    ing1.setName("Lạc");
    ing1.setActive(true);

    Ingredient ing2 = new Ingredient();
    ing2.setId(id2);
    ing2.setName("Sữa bò");
    ing2.setActive(false);

    when(ingredientRepository.findAllByIds(Set.of(id1, id2))).thenReturn(List.of(ing1, ing2));

    Map<UUID, IngredientSummary> result = adapter.findSummariesByIds(Set.of(id1, id2));

    assertThat(result).hasSize(2);
    assertThat(result.get(id1)).isEqualTo(new IngredientSummary(id1, "Lạc", true));
    assertThat(result.get(id2)).isEqualTo(new IngredientSummary(id2, "Sữa bò", false));
  }

  @Test
  @DisplayName("findCommonAllergens: trả về danh sách allergen hệ thống")
  void testFindCommonAllergens() {
    Ingredient ing = new Ingredient();
    ing.setId(UUID.randomUUID());
    ing.setName("Trứng");
    ing.setAllergen(true);
    ing.setSource(FoodSource.SYSTEM);
    ing.setActive(true);

    when(ingredientRepository.findAllCommonAllergens()).thenReturn(List.of(ing));

    List<IngredientSummary> result = adapter.findCommonAllergens();

    assertThat(result).hasSize(1);
    assertThat(result.get(0).name()).isEqualTo("Trứng");
    assertThat(result.get(0).active()).isTrue();
  }
}
