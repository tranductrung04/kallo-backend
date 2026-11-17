package com.healthcare.kallo.service.chat;

import com.healthcare.kallo.config.ChatProperties;
import com.healthcare.kallo.repository.chat.ChatMessageRepository;
import com.healthcare.kallo.repository.chat.ConversationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ChatRetentionJob {
  private static final Logger log = LoggerFactory.getLogger(ChatRetentionJob.class);
  private final ConversationRepository conversations;
  private final ChatMessageRepository messages;
  private final ChatProperties properties;
  private final Clock clock;

  public ChatRetentionJob(ConversationRepository conversations, ChatMessageRepository messages,
      ChatProperties properties, Clock clock) {
    this.conversations = conversations;
    this.messages = messages;
    this.properties = properties;
    this.clock = clock;
  }

  @Scheduled(cron = "0 30 3 * * *", zone = "UTC")
  @Transactional
  public void deleteExpiredConversations() {
    Instant cutoff = Instant.now(clock).minus(properties.retentionDays(), ChronoUnit.DAYS);
    int deletedMessages = messages.deleteSoftDeletedBefore(cutoff);
    int deletedConversations = conversations.deleteSoftDeletedBefore(cutoff);
    log.info("Deleted {} expired chat messages and {} conversations", deletedMessages, deletedConversations);
  }
}
