package com.healthcare.kallo.dto.response.sys;

import java.util.List;
import java.util.Map;

public record AttributionResponse(List<Source> sources, Map<String, Map<String, String>> foodSourceLabels) {
  public record Source(String name, String license, String url, String attribution) {}
}
