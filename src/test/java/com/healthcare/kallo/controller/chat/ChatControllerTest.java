package com.healthcare.kallo.controller.chat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.config.RateLimitTestConfig;
import com.healthcare.kallo.service.chat.ChatService;
import com.healthcare.kallo.service.chat.ChatSseService;
import com.healthcare.kallo.config.ChatProperties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(value = ChatController.class, properties = "spring.profiles.active=test")
@Import(RateLimitTestConfig.class)
@AutoConfigureMockMvc
class ChatControllerTest {
  @Autowired MockMvc mockMvc;
  @MockitoBean ChatService chat;
  @MockitoBean ChatSseService sse;
  @MockitoBean ChatProperties properties;

  @Test
  void rejectsOversizedMessageBeforeService() throws Exception {
    UUID userId = UUID.randomUUID();
    mockMvc.perform(post("/api/chat/messages")
            .with(jwt().jwt(Jwt.withTokenValue("token").subject(userId.toString()).header("alg", "none").build()))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"content\":\"" + "x".repeat(2001) + "\",\"clientMessageId\":\""
                + UUID.randomUUID() + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
  }

  @Test
  void returnsRetryAfterForRateLimitedMessage() throws Exception {
    UUID userId = UUID.randomUUID();
    when(chat.sendMessage(any(), any())).thenThrow(new BusinessException(ErrorCode.TOO_MANY_REQUESTS, 17));

    mockMvc.perform(post("/api/chat/messages")
            .with(jwt().jwt(Jwt.withTokenValue("token").subject(userId.toString()).header("alg", "none").build()))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"content\":\"hello\",\"clientMessageId\":\"" + UUID.randomUUID() + "\"}"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("Retry-After", "17"))
        .andExpect(jsonPath("$.error.code").value("TOO_MANY_REQUESTS"));
  }
}
