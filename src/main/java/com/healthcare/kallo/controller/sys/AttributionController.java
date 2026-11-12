package com.healthcare.kallo.controller.sys;

import com.healthcare.kallo.common.ApiResponse;
import com.healthcare.kallo.config.AttributionProperties;
import com.healthcare.kallo.dto.response.sys.AttributionResponse;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app")
public class AttributionController {
  private final AttributionProperties properties;

  public AttributionController(AttributionProperties properties) {
    this.properties = properties;
  }

  @GetMapping("/attributions")
  public ApiResponse<AttributionResponse> getAttributions() {
    return ApiResponse.success(
        new AttributionResponse(
            List.of(
                new AttributionResponse.Source(
                    "Open Food Facts",
                    properties.openFoodFactsLicense(),
                    properties.openFoodFactsUrl(),
                    properties.openFoodFactsAttribution()),
                new AttributionResponse.Source(
                    "NIN",
                    properties.ninLicense(),
                    properties.ninUrl(),
                    properties.ninAttribution()),
                new AttributionResponse.Source(
                    "FAO/INFOODS",
                    properties.faoInfoodsLicense(),
                    properties.faoInfoodsUrl(),
                    properties.faoInfoodsAttribution())),
            Map.of(
                "SYSTEM", Map.of("vi", "Dữ liệu hệ thống", "en", "System data"),
                "NIN", Map.of("vi", "NIN", "en", "NIN"),
                "OFF", Map.of("vi", "Open Food Facts", "en", "Open Food Facts"),
                "USER", Map.of("vi", "Tự tạo", "en", "User-created"))));
  }
}
