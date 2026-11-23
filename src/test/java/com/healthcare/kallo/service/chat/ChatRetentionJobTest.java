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
      new ChatProperties.Ai("http://ai", "key", Duration.ofSeconds(2), Duration.ofSeconds(30), true),
      12, 2000, new ChatProperties.RateLimit(10, 200), Duration.ofMinutes(2), 30,
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
