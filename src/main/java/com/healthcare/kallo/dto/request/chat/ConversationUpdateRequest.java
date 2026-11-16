package com.healthcare.kallo.dto.request.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConversationUpdateRequest(@NotBlank @Size(max = 120) String title) {}
