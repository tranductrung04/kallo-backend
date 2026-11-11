package com.healthcare.kallo.controller.act;

import com.healthcare.kallo.common.ApiResponse;
import com.healthcare.kallo.dto.response.act.ActivityTypeResponse;
import com.healthcare.kallo.service.act.ActivityTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/activity-types")
@Tag(name = "Activity types", description = "Exercise MET catalogue")
public class ActivityTypeController {
  private final ActivityTypeService types;
  public ActivityTypeController(ActivityTypeService types) { this.types = types; }

  @GetMapping
  @Operation(summary = "List active activity types")
  public ResponseEntity<ApiResponse<List<ActivityTypeResponse>>> list() {
    return ResponseEntity.ok().cacheControl(CacheControl.maxAge(java.time.Duration.ofHours(1)))
        .body(ApiResponse.success(types.listActive()));
  }
}
