package com.healthcare.kallo.docs;

import static org.assertj.core.api.Assertions.assertThat;

import com.healthcare.kallo.controller.food.BarcodeController;
import com.healthcare.kallo.controller.food.FoodController;
import com.healthcare.kallo.controller.food.IngredientController;
import com.healthcare.kallo.controller.nutri.DailySummaryController;
import com.healthcare.kallo.controller.nutri.FoodLogController;
import com.healthcare.kallo.controller.nutri.WaterLogController;
import com.healthcare.kallo.controller.plan.MealPlanController;
import com.healthcare.kallo.controller.profile.AllergyController;
import com.healthcare.kallo.controller.profile.ChronicConditionController;
import com.healthcare.kallo.controller.profile.ProfileController;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.yaml.snakeyaml.Yaml;

class OpenApiSpecContractTest {

  @Test
  void nutriPlanFoodAndProfileSpecsAreValidYamlWithZeroBrokenRefs() throws Exception {
    for (String filename : List.of("nutri.yaml", "plan.yaml", "food.yaml", "profile.yaml")) {
      Map<String, Object> root = loadSpec(filename);
      assertThat(root.get("openapi")).as(filename).isEqualTo("3.1.0");
      assertThat(root).containsKeys("info", "paths", "components");

      Set<String> refs = new LinkedHashSet<>();
      collectRefs(root, refs);
      assertThat(refs).as(filename + " refs").isNotEmpty();

      for (String ref : refs) {
        assertThat(ref).startsWith("#/");
        Object target = resolveJsonPointer(root, ref);
        assertThat(target)
            .as("Unresolved $ref '%s' in %s", ref, filename)
            .isNotNull();
      }
    }
  }

  @Test
  void nutriAndPlanSpecsMatchAllControllerEndpointsExactly() throws Exception {
    Set<String> nutriActual =
        extractEndpoints(
            FoodLogController.class, WaterLogController.class, DailySummaryController.class);
    Set<String> nutriSpec = extractSpecEndpoints(loadSpec("nutri.yaml"));
    assertThat(nutriSpec).containsExactlyInAnyOrderElementsOf(nutriActual);

    Set<String> planActual = extractEndpoints(MealPlanController.class);
    Set<String> planSpec = extractSpecEndpoints(loadSpec("plan.yaml"));
    assertThat(planSpec).containsExactlyInAnyOrderElementsOf(planActual);

    Set<String> foodActual =
        extractEndpoints(
            FoodController.class, IngredientController.class, BarcodeController.class);
    Set<String> foodSpec = extractSpecEndpoints(loadSpec("food.yaml"));
    assertThat(foodSpec).containsExactlyInAnyOrderElementsOf(foodActual);

    Set<String> profileActual =
        extractEndpoints(
            ProfileController.class, AllergyController.class, ChronicConditionController.class);
    Set<String> profileSpec = extractSpecEndpoints(loadSpec("profile.yaml"));
    assertThat(profileSpec).containsExactlyInAnyOrderElementsOf(profileActual);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> loadSpec(String filename) throws Exception {
    Path path = Path.of("docs", "api-specs", filename);
    assertThat(Files.exists(path)).as("Spec file %s", path).isTrue();
    try (InputStream in = Files.newInputStream(path)) {
      Object parsed = new Yaml().load(in);
      assertThat(parsed).isInstanceOf(Map.class);
      return (Map<String, Object>) parsed;
    }
  }

  private void collectRefs(Object node, Set<String> refs) {
    if (node instanceof Map<?, ?> map) {
      Object ref = map.get("$ref");
      if (ref instanceof String s) {
        refs.add(s);
      }
      for (Object value : map.values()) {
        collectRefs(value, refs);
      }
    } else if (node instanceof List<?> list) {
      for (Object item : list) {
        collectRefs(item, refs);
      }
    }
  }

  private Object resolveJsonPointer(Map<String, Object> root, String ref) {
    String[] parts = ref.substring(2).split("/");
    Object current = root;
    for (String part : parts) {
      if (!(current instanceof Map<?, ?> map)) {
        return null;
      }
      String unescaped = part.replace("~1", "/").replace("~0", "~");
      current = map.get(unescaped);
      if (current == null) {
        return null;
      }
    }
    return current;
  }

  @SuppressWarnings("unchecked")
  private Set<String> extractSpecEndpoints(Map<String, Object> root) {
    Map<String, Object> paths = (Map<String, Object>) root.get("paths");
    Set<String> endpoints = new LinkedHashSet<>();
    for (Map.Entry<String, Object> entry : paths.entrySet()) {
      Map<String, Object> pathItem = (Map<String, Object>) entry.getValue();
      for (String method : List.of("get", "post", "put", "delete", "patch")) {
        if (pathItem.containsKey(method)) {
          endpoints.add(method.toUpperCase() + " " + entry.getKey());
        }
      }
    }
    return endpoints;
  }

  private Set<String> extractEndpoints(Class<?>... controllers) {
    Set<String> endpoints = new LinkedHashSet<>();
    for (Class<?> controller : controllers) {
      RequestMapping classMapping = controller.getAnnotation(RequestMapping.class);
      String prefix =
          classMapping != null && classMapping.value().length > 0 ? classMapping.value()[0] : "";
      for (Method method : controller.getDeclaredMethods()) {
        addMappedEndpoint(endpoints, prefix, "GET", method.getAnnotation(GetMapping.class) != null ? method.getAnnotation(GetMapping.class).value() : null);
        addMappedEndpoint(endpoints, prefix, "POST", method.getAnnotation(PostMapping.class) != null ? method.getAnnotation(PostMapping.class).value() : null);
        addMappedEndpoint(endpoints, prefix, "PUT", method.getAnnotation(PutMapping.class) != null ? method.getAnnotation(PutMapping.class).value() : null);
        addMappedEndpoint(endpoints, prefix, "DELETE", method.getAnnotation(DeleteMapping.class) != null ? method.getAnnotation(DeleteMapping.class).value() : null);
      }
    }
    return endpoints;
  }

  private void addMappedEndpoint(Set<String> endpoints, String prefix, String httpMethod, String[] paths) {
    if (paths == null) {
      return;
    }
    if (paths.length == 0) {
      endpoints.add(httpMethod + " " + prefix);
      return;
    }
    for (String sub : paths) {
      endpoints.add(httpMethod + " " + prefix + sub);
    }
  }
}
