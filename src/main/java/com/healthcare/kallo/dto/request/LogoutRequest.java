package com.healthcare.kallo.dto.request;

public record LogoutRequest(String refreshToken, Boolean allDevices) {}
