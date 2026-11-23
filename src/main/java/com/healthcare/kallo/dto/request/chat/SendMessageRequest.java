package com.healthcare.kallo.dto.request.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record SendMessageRequest(
    UUID conversationId,
    @NotBlank @Size(max = 2000) String content,
    @NotNull UUID clientMessageId) {}
