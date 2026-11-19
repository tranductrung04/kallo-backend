package com.healthcare.kallo.service.food.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OpenFoodFactsClient {

  private static final Logger log = LoggerFactory.getLogger(OpenFoodFactsClient.class);
  private final RestClient restClient;

  public OpenFoodFactsClient() {
    this("https://world.openfoodfacts.org/api/v2");
  }

  public OpenFoodFactsClient(String baseUrl) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(2000);
    factory.setReadTimeout(2500);

    this.restClient =
        RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(factory)
            .defaultHeader(HttpHeaders.USER_AGENT, "KalloNutritionApp/1.0 (contact@kallo.vn)")
            .build();
  }

  public Optional<OffProduct> fetchByBarcode(String barcode) {
    try {
      OffResponse response =
          restClient
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path("/product/{barcode}")
                          .queryParam(
                              "fields",
                              "code,product_name,product_name_vi,brands,nutriments,serving_size,serving_quantity,image_front_url,allergens_tags")
                          .build(barcode))
              .retrieve()
              .body(OffResponse.class);

      if (response != null && response.status() == 1 && response.product() != null) {
        return Optional.of(response.product());
      }
      return Optional.empty();
    } catch (Exception ex) {
      log.warn("Error querying Open Food Facts API for barcode {}: {}", barcode, ex.getMessage());
      return Optional.empty();
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record OffResponse(int status, OffProduct product) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record OffProduct(
      String code,
      @JsonProperty("product_name") String productName,
      @JsonProperty("product_name_vi") String productNameVi,
      String brands,
      @JsonProperty("serving_size") String servingSize,
      @JsonProperty("serving_quantity") Double servingQuantity,
      @JsonProperty("image_front_url") String imageFrontUrl,
      @JsonProperty("allergens_tags") List<String> allergensTags,
      Map<String, Object> nutriments) {

    public String resolveDisplayName() {
      if (productNameVi != null && !productNameVi.isBlank()) {
        return productNameVi.trim();
      }
      if (productName != null && !productName.isBlank()) {
        return productName.trim();
      }
      return "Sản phẩm " + code;
    }

    public Double getNutrimentValue(String... keys) {
      if (nutriments == null) {
        return null;
      }
      for (String key : keys) {
        Object val = nutriments.get(key);
        if (val instanceof Number num) {
          return num.doubleValue();
        } else if (val instanceof String str) {
          try {
            return Double.parseDouble(str);
          } catch (NumberFormatException ignored) {
          }
        }
      }
      return null;
    }

    public Double getCalories100g() {
      return getNutrimentValue("energy-kcal_100g", "energy-kcal", "energy-kcal_value");
    }

    public Double getProtein100g() {
      return getNutrimentValue("proteins_100g", "proteins", "proteins_value");
    }

    public Double getCarbs100g() {
      return getNutrimentValue("carbohydrates_100g", "carbohydrates", "carbohydrates_value");
    }

    public Double getFat100g() {
      return getNutrimentValue("fat_100g", "fat", "fat_value");
    }

    public Double getSugar100g() {
      return getNutrimentValue("sugars_100g", "sugars", "sugars_value");
    }

    public Double getFiber100g() {
      return getNutrimentValue("fiber_100g", "fiber", "fiber_value");
    }
  }
}
