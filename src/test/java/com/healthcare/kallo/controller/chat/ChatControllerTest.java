package com.healthcare.kallo.controller.chat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.config.RateLimitTestConfig;
import com.healthcare.kallo.service.chat.ChatFeedbackService;
import com.healthcare.kallo.service.chat.ChatService;
import com.healthcare.kallo.service.chat.ChatSuggestionService;
import com.healthcare.kallo.service.chat.ChatTxService;
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
  @MockitoBean ChatTxService tx;
  @MockitoBean ChatFeedbackService feedback;
  @MockitoBean ChatSuggestionService suggestions;

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
  void releasesSseSlotWhenWorkerPoolIsFull() throws Exception {
    UUID userId = UUID.randomUUID();
    when(properties.sseTimeout()).thenReturn(java.time.Duration.ofSeconds(60));
    when(properties.sseHeartbeatSeconds()).thenReturn(15);
    when(sse.scheduleHeartbeat(any(), anyLong())).thenReturn(org.mockito.Mockito.mock(java.util.concurrent.ScheduledFuture.class));
    ChatTxService.SavedMessage saved = org.mockito.Mockito.mock(ChatTxService.SavedMessage.class);
    when(chat.prepareStreamMessage(any(), any())).thenReturn(saved);
    doThrow(new com.healthcare.kallo.service.chat.ChatSseException(
        "CHAT_STREAM_CAPACITY", "Too many active chat streams")).when(sse).execute(any());

    mockMvc.perform(post("/api/chat/messages/stream")
            .with(jwt().jwt(Jwt.withTokenValue("token").subject(userId.toString()).header("alg", "none").build()))
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
            .content("{\"content\":\"hello\",\"clientMessageId\":\"" + UUID.randomUUID() + "\"}"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.error.code").value("CHAT_STREAM_CAPACITY"));

    org.mockito.Mockito.verify(sse).openConnection();
    org.mockito.Mockito.verify(sse).closeConnection();
    org.mockito.Mockito.verify(chat).markStreamFailed(saved, "CHAT_STREAM_CAPACITY");
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
