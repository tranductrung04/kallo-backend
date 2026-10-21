package com.healthcare.kallo.dto.request;

import jakarta.validation.constraints.Size;

public record DeleteAccountRequest(
    @Size(max = 200) String password,
    @Size(max = 4096) String idToken) {}
