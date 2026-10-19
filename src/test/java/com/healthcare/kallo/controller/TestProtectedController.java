package com.healthcare.kallo.controller;

import com.healthcare.kallo.common.ApiResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("test")
@RestController
@RequestMapping("/api/test")
public class TestProtectedController {
  @GetMapping("/protected")
  public ApiResponse<String> protectedEndpoint() {
    return ApiResponse.success("authenticated");
  }
}
