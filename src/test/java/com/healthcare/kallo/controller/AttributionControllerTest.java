package com.healthcare.kallo.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.healthcare.kallo.config.AttributionProperties;
import com.healthcare.kallo.controller.sys.AttributionController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(value = AttributionController.class, properties = "spring.profiles.active=test")
@AutoConfigureMockMvc(addFilters = false)
@Import(AttributionControllerTest.PropertiesConfig.class)
class AttributionControllerTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void returnsOpenFoodFactsAttributionAndSourceLabels() throws Exception {
    mockMvc
        .perform(get("/api/app/attributions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.sources[0].license").value("ODbL-1.0"))
        .andExpect(jsonPath("$.data.foodSourceLabels.OFF.vi").value("Open Food Facts"));
  }

  @TestConfiguration
  static class PropertiesConfig {
    @Bean
    AttributionProperties attributionProperties() {
      return new AttributionProperties(
          "https://world.openfoodfacts.org",
          "ODbL-1.0",
          "Data from Open Food Facts.",
          "https://viendinhduong.vn/",
          "Vietnamese Food Composition Table terms",
          "Dữ liệu từ Viện Dinh dưỡng Quốc gia / Data from the Vietnam National Institute of Nutrition.",
          "https://www.fao.org/infoods",
          "FAO/INFOODS terms of use",
          "Dữ liệu từ FAO/INFOODS / Data from FAO/INFOODS.");
    }
  }
}
