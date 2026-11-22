package com.healthcare.kallo.controller.act;

import com.healthcare.kallo.common.*;
import com.healthcare.kallo.dto.request.act.*;
import com.healthcare.kallo.dto.response.act.BodyMetricResponse;
import com.healthcare.kallo.security.auth.CurrentUser;
import com.healthcare.kallo.service.act.BodyMetricService;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/body-metrics")
public class BodyMetricController {
  private final BodyMetricService metrics;
  public BodyMetricController(BodyMetricService metrics) { this.metrics = metrics; }

  @GetMapping
  public ApiResponse<java.util.List<BodyMetricResponse>> list(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      Authentication authentication) { return ApiResponse.success(metrics.list(CurrentUser.id(authentication), from, to)); }

  @GetMapping("/{id}")
  public ApiResponse<BodyMetricResponse> get(@PathVariable UUID id, Authentication authentication) {
    return ApiResponse.success(metrics.getById(CurrentUser.id(authentication), id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND)));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<BodyMetricResponse>> create(@Valid @RequestBody CreateBodyMetricRequest request, Authentication authentication) {
    var result = metrics.create(CurrentUser.id(authentication), request);
    return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .location(URI.create("/api/body-metrics/" + result.response().id())).body(ApiResponse.success(result.response()));
  }

  @PutMapping("/{id}")
  public ApiResponse<BodyMetricResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateBodyMetricRequest request, Authentication authentication) {
    return ApiResponse.success(metrics.update(CurrentUser.id(authentication), id, request));
  }

  @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id, Authentication authentication) { metrics.delete(CurrentUser.id(authentication), id); }
}
