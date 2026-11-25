package com.healthcare.kallo.service.food.client;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Slf4j
public class OpenFoodFactsClient {

  private final RestClient restClient;
  private final ObjectMapper objectMapper;

  public OpenFoodFactsClient(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofMillis(2000));
    requestFactory.setReadTimeout(Duration.ofMillis(2500));

    this.restClient = RestClient.builder()
        .baseUrl("https://world.openfoodfacts.org")
        .requestFactory(requestFactory)
        .defaultHeader("User-Agent", "KalloApp - Android/iOS - Version 1.0")
        .build();
  }

  public record OffProductDto(
      String barcode,
      String name,
      BigDecimal caloriesPer100g,
      BigDecimal proteinPer100g,
      BigDecimal carbsPer100g,
      BigDecimal fatPer100g,
      BigDecimal sugarPer100g,
      BigDecimal fiberPer100g,
      String defaultServingName,
      BigDecimal defaultServingGrams,
      String imageUrl
  ) {}

  public Optional<OffProductDto> fetchProduct(String barcode) {
    try {
      String responseBody = restClient.get()
          .uri("/api/v2/product/{barcode}.json", barcode)
          .retrieve()
          .body(String.class);

      if (responseBody == null || responseBody.isBlank()) {
        return Optional.empty();
      }

      JsonNode root = objectMapper.readTree(responseBody);
      int status = root.path("status").asInt(0);
      if (status != 1 || !root.hasNonNull("product")) {
        return Optional.empty();
      }

      JsonNode product = root.path("product");
      String name = product.path("product_name").asText(product.path("product_name_vi").asText("Unknown Product"));
      if (name.isBlank()) {
        name = "Sản phẩm mã vạch " + barcode;
      }

      JsonNode nutriments = product.path("nutriments");
      BigDecimal cal = BigDecimal.valueOf(nutriments.path("energy-kcal_100g").asDouble(0.0)).setScale(1, RoundingMode.HALF_UP);
      BigDecimal pro = BigDecimal.valueOf(nutriments.path("proteins_100g").asDouble(0.0)).setScale(1, RoundingMode.HALF_UP);
      BigDecimal carb = BigDecimal.valueOf(nutriments.path("carbohydrates_100g").asDouble(0.0)).setScale(1, RoundingMode.HALF_UP);
      BigDecimal fat = BigDecimal.valueOf(nutriments.path("fat_100g").asDouble(0.0)).setScale(1, RoundingMode.HALF_UP);
      BigDecimal sugar = BigDecimal.valueOf(nutriments.path("sugars_100g").asDouble(0.0)).setScale(1, RoundingMode.HALF_UP);
      BigDecimal fiber = BigDecimal.valueOf(nutriments.path("fiber_100g").asDouble(0.0)).setScale(1, RoundingMode.HALF_UP);

      String servingName = product.path("serving_size").asText("1 phần (100g)");
      double servingGramsVal = product.path("serving_quantity").asDouble(100.0);
      if (servingGramsVal <= 0) {
        servingGramsVal = 100.0;
      }
      BigDecimal servingGrams = BigDecimal.valueOf(servingGramsVal).setScale(1, RoundingMode.HALF_UP);

      String imageUrl = product.path("image_front_url").asText(null);

      return Optional.of(new OffProductDto(
          barcode,
          name,
          cal,
          pro,
          carb,
          fat,
          sugar,
          fiber,
          servingName,
          servingGrams,
          imageUrl
      ));
    } catch (Exception e) {
      log.warn("Failed or timed out calling Open Food Facts for barcode {}: {}", barcode, e.getMessage());
      return Optional.empty();
    }
  }
}
