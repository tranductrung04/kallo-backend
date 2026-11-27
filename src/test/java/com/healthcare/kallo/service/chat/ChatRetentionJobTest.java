package com.healthcare.kallo.service.chat;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.healthcare.kallo.config.ChatProperties;
import com.healthcare.kallo.repository.chat.ChatMessageRepository;
import com.healthcare.kallo.repository.chat.ConversationRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ChatRetentionJobTest {
  private final ConversationRepository conversations = Mockito.mock(ConversationRepository.class);
  private final ChatMessageRepository messages = Mockito.mock(ChatMessageRepository.class);
  private final ChatProperties properties = new ChatProperties(true,
      new ChatProperties.Llm("http://localhost:11434/v1", "", "qwen2.5:7b",
          Duration.ofSeconds(5), Duration.ofSeconds(60), 0.4, 1024),
      12, 4500, 2000, new ChatProperties.RateLimit(10, 200), Duration.ofSeconds(120), 30,
      Duration.ofSeconds(60), 15);

  @Test
  void deletesSoftDeletedRowsBeforeRetentionCutoff() {
    when(messages.deleteSoftDeletedBefore(Instant.parse("2026-10-18T00:00:00Z"))).thenReturn(3);
    when(conversations.deleteSoftDeletedBefore(Instant.parse("2026-10-18T00:00:00Z"))).thenReturn(1);
    ChatRetentionJob job = new ChatRetentionJob(conversations, messages, properties,
        Clock.fixed(Instant.parse("2026-11-17T00:00:00Z"), ZoneOffset.UTC));

    job.deleteExpiredConversations();

    verify(messages).deleteSoftDeletedBefore(Instant.parse("2026-10-18T00:00:00Z"));
    verify(conversations).deleteSoftDeletedBefore(Instant.parse("2026-10-18T00:00:00Z"));
  }
}
