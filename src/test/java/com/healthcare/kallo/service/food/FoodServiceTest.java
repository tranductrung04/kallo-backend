package com.healthcare.kallo.service.food;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.common.PageResponse;
import com.healthcare.kallo.dto.request.food.CustomFoodRequest;
import com.healthcare.kallo.dto.response.food.FoodResponse;
import com.healthcare.kallo.dto.response.food.FoodSearchItemResponse;
import com.healthcare.kallo.model.auth.User;
import com.healthcare.kallo.model.food.Food;
import com.healthcare.kallo.model.food.enums.FoodCategory;
import com.healthcare.kallo.model.food.enums.FoodSource;
import com.healthcare.kallo.model.food.enums.MealType;
import com.healthcare.kallo.repository.auth.UserRepository;
import com.healthcare.kallo.repository.food.FoodRepository;
import com.healthcare.kallo.service.food.impl.FoodServiceImpl;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class FoodServiceTest {

  @Mock private FoodRepository foodRepository;
  @Mock private UserRepository userRepository;

  private FoodService foodService;

  @BeforeEach
  void setUp() {
    foodService = new FoodServiceImpl(foodRepository, userRepository);
  }

  @Test
  @DisplayName("searchFoods() queries repository and maps to page response")
  void searchFoods_success() {
    UUID userId = UUID.randomUUID();
    Food food = new Food();
    food.setId(UUID.randomUUID());
    food.setName("Phở bò");
    food.setCategory(FoodCategory.NOODLES_SOUP);
    food.setDefaultServingName("1 tô vừa");
    food.setDefaultServingGrams(BigDecimal.valueOf(500.0));
    food.setCaloriesPerServing(525);
    food.setProteinPerServing(BigDecimal.valueOf(37.5));
    food.setCarbsPerServing(BigDecimal.valueOf(66.0));
    food.setFatPerServing(BigDecimal.valueOf(11.5));
    food.setSource(FoodSource.SYSTEM);
    food.setVerified(true);

    when(foodRepository.searchFoods(eq(userId), eq("pho"), eq("NOODLES_SOUP"), any()))
        .thenReturn(new PageImpl<>(List.of(food), PageRequest.of(0, 10), 1));

    PageResponse<FoodSearchItemResponse> response =
        foodService.searchFoods(userId, "pho", FoodCategory.NOODLES_SOUP, PageRequest.of(0, 10));

    assertThat(response.items()).hasSize(1);
    assertThat(response.items().get(0).name()).isEqualTo("Phở bò");
    assertThat(response.totalElements()).isEqualTo(1);
  }

  @Test
  @DisplayName("getFoodById() returns food when system food exists")
  void getFoodById_systemFood() {
    UUID foodId = UUID.randomUUID();
    UUID currentUserId = UUID.randomUUID();

    Food food = new Food();
    food.setId(foodId);
    food.setName("Bánh mì");
    food.setNameUnaccented("banh mi");
    food.setCategory(FoodCategory.BREAD_SANDWICH);
    food.setDefaultServingName("1 ổ");
    food.setDefaultServingGrams(BigDecimal.valueOf(180.0));
    food.setCaloriesPer100g(BigDecimal.valueOf(250.0));
    food.setProteinPer100g(BigDecimal.valueOf(9.5));
    food.setCarbsPer100g(BigDecimal.valueOf(33.0));
    food.setFatPer100g(BigDecimal.valueOf(8.8));
    food.setCaloriesPerServing(450);
    food.setProteinPerServing(BigDecimal.valueOf(17.1));
    food.setCarbsPerServing(BigDecimal.valueOf(59.4));
    food.setFatPerServing(BigDecimal.valueOf(15.8));
    food.setOwner(null);

    when(foodRepository.findById(foodId)).thenReturn(Optional.of(food));

    FoodResponse response = foodService.getFoodById(foodId, currentUserId);
    assertThat(response).isNotNull();
    assertThat(response.name()).isEqualTo("Bánh mì");
  }

  @Test
  @DisplayName("getFoodById() hides custom food of other users (IDOR protection)")
  void getFoodById_otherUserCustomFood() {
    UUID foodId = UUID.randomUUID();
    UUID userA = UUID.randomUUID();
    UUID userB = UUID.randomUUID();

    User ownerB = new User();
    ownerB.setId(userB);

    Food food = new Food();
    food.setId(foodId);
    food.setOwner(ownerB);

    when(foodRepository.findById(foodId)).thenReturn(Optional.of(food));

    assertThatThrownBy(() -> foodService.getFoodById(foodId, userA))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FOOD_NOT_FOUND);
  }

  @Test
  @DisplayName("getFoodByVietfoodClassId() returns valid food")
  void getFoodByVietfoodClassId_valid() {
    Food food = new Food();
    food.setId(UUID.randomUUID());
    food.setName("Phở bò");
    food.setNameUnaccented("pho bo");
    food.setCategory(FoodCategory.NOODLES_SOUP);
    food.setDefaultServingName("1 tô vừa");
    food.setDefaultServingGrams(BigDecimal.valueOf(500.0));
    food.setCaloriesPer100g(BigDecimal.valueOf(105.0));
    food.setProteinPer100g(BigDecimal.valueOf(7.5));
    food.setCarbsPer100g(BigDecimal.valueOf(13.2));
    food.setFatPer100g(BigDecimal.valueOf(2.3));
    food.setCaloriesPerServing(525);
    food.setProteinPerServing(BigDecimal.valueOf(37.5));
    food.setCarbsPerServing(BigDecimal.valueOf(66.0));
    food.setFatPerServing(BigDecimal.valueOf(11.5));
    food.setVietfoodClassId(46);

    when(foodRepository.findByVietfoodClassId(46)).thenReturn(Optional.of(food));

    FoodResponse response = foodService.getFoodByVietfoodClassId(46);
    assertThat(response.vietfoodClassId()).isEqualTo(46);
    assertThat(response.name()).isEqualTo("Phở bò");
  }

  @Test
  @DisplayName("getFoodByVietfoodClassId() throws NON_FOOD_DETECTED for class 27 (Con nguoi)")
  void getFoodByVietfoodClassId_nonFood() {
    Food nonFood = new Food();
    nonFood.setId(UUID.randomUUID());
    nonFood.setName("Người");
    nonFood.setCategory(FoodCategory.NON_FOOD);
    nonFood.setVietfoodClassId(27);

    when(foodRepository.findByVietfoodClassId(27)).thenReturn(Optional.of(nonFood));

    assertThatThrownBy(() -> foodService.getFoodByVietfoodClassId(27))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NON_FOOD_DETECTED);
  }

  @Test
  @DisplayName("createCustomFood() calculates serving nutrition and saves custom food")
  void createCustomFood_success() {
    UUID userId = UUID.randomUUID();
    User user = new User();
    user.setId(userId);

    CustomFoodRequest request =
        new CustomFoodRequest(
            "Cơm sườn đặc biệt",
            FoodCategory.RICE_DISH,
            "1 dĩa",
            BigDecimal.valueOf(400.0),
            BigDecimal.valueOf(200.0),
            BigDecimal.valueOf(10.0),
            BigDecimal.valueOf(25.0),
            BigDecimal.valueOf(7.0),
            BigDecimal.valueOf(4.0),
            BigDecimal.valueOf(2.0),
            "https://image.url",
            null,
            List.of("SOY"),
            List.of(MealType.LUNCH, MealType.DINNER),
            60000);

    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(foodRepository.save(any(Food.class))).thenAnswer(i -> {
      Food f = i.getArgument(0);
      f.setId(UUID.randomUUID());
      return f;
    });

    FoodResponse response = foodService.createCustomFood(userId, request);

    assertThat(response).isNotNull();
    assertThat(response.name()).isEqualTo("Cơm sườn đặc biệt");
    assertThat(response.nameUnaccented()).isEqualTo("com suon dac biet");
    assertThat(response.caloriesPerServing()).isEqualTo(800); // 200 * 4
    assertThat(response.proteinPerServing()).isEqualByComparingTo(BigDecimal.valueOf(40.0)); // 10 * 4
    assertThat(response.source()).isEqualTo(FoodSource.USER_CUSTOM);
  }

  @Test
  @DisplayName("updateCustomFood() throws CANNOT_MODIFY_SYSTEM_FOOD when modifying system food")
  void updateCustomFood_systemFood() {
    UUID foodId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    Food systemFood = new Food();
    systemFood.setId(foodId);
    systemFood.setOwner(null); // system food

    when(foodRepository.findById(foodId)).thenReturn(Optional.of(systemFood));

    CustomFoodRequest request =
        new CustomFoodRequest(
            "Test", FoodCategory.SNACK, "1 phần", BigDecimal.valueOf(100.0),
            BigDecimal.valueOf(100.0), BigDecimal.valueOf(1.0), BigDecimal.valueOf(1.0),
            BigDecimal.valueOf(1.0), BigDecimal.ZERO, BigDecimal.ZERO, null, null, null, null, null);

    assertThatThrownBy(() -> foodService.updateCustomFood(userId, foodId, request))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CANNOT_MODIFY_SYSTEM_FOOD);
  }

  @Test
  @DisplayName("deleteCustomFood() throws FORBIDDEN when user is not owner")
  void deleteCustomFood_notOwner() {
    UUID foodId = UUID.randomUUID();
    UUID userA = UUID.randomUUID();
    UUID userB = UUID.randomUUID();

    User ownerB = new User();
    ownerB.setId(userB);

    Food food = new Food();
    food.setId(foodId);
    food.setOwner(ownerB);

    when(foodRepository.findById(foodId)).thenReturn(Optional.of(food));

    assertThatThrownBy(() -> foodService.deleteCustomFood(userA, foodId))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
  }

  @Test
  @DisplayName("unaccent() correctly removes accents from Vietnamese string")
  void unaccent_test() {
    assertThat(FoodServiceImpl.unaccent("Phở bò đặc biệt")).isEqualTo("pho bo dac biet");
    assertThat(FoodServiceImpl.unaccent("Bún riêu cua đồng")).isEqualTo("bun rieu cua dong");
    assertThat(FoodServiceImpl.unaccent("Cơm tấm sườn bì chả")).isEqualTo("com tam suon bi cha");
  }
}
