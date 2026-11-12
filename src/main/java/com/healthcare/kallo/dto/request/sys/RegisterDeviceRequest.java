package com.healthcare.kallo.dto.request.sys;

import com.healthcare.kallo.model.sys.enums.DevicePlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterDeviceRequest(
    @NotBlank @Size(max = 512) String token,
    @NotNull DevicePlatform platform,
    @Size(max = 10) String locale,
    @Size(max = 20) String appVersion) {}
