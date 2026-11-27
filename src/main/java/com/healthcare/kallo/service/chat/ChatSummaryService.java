package com.healthcare.kallo.service.chat;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ChatSummaryService {
  private static final Logger log = LoggerFactory.getLogger(ChatSummaryService.class);
  static final int SUMMARY_BATCH_SIZE = 10;
  private static final int MAX_SUMMARY_CHARS = 1_200;

  private final ChatTxService tx;
  private final ChatAiClient aiClient;
  private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

  public ChatSummaryService(ChatTxService tx, ChatAiClient aiClient) {
    this.tx = tx;
    this.aiClient = aiClient;
  }

  @Async("chatTaskExecutor")
  public void summarizeIfNeeded(UUID conversationId, int historyWindow) {
    if (conversationId == null || !inFlight.add(conversationId)) return;
    try {
      ChatTxService.SummarySnapshot snapshot = tx.loadSummarySnapshot(conversationId, historyWindow, SUMMARY_BATCH_SIZE);
      if (snapshot == null || snapshot.unsummarizedMessages().isEmpty()) return;

      String updated = aiClient.summarize(snapshot.existingSummary(), snapshot.unsummarizedMessages());
      if (updated == null || updated.isBlank()) return;

      String bounded = boundSummary(updated.trim());
      tx.saveSummaryWithWatermark(
          conversationId,
          snapshot.expectedWatermarkId(),
          snapshot.newWatermarkId(),
          bounded);
    } catch (RuntimeException exception) {
      log.warn("Failed to refresh rolling chat summary conversationId={}", conversationId, exception);
    } finally {
      inFlight.remove(conversationId);
    }
  }

  private String boundSummary(String text) {
    if (text.length() <= MAX_SUMMARY_CHARS) return text;
    return text.substring(0, MAX_SUMMARY_CHARS).trim();
  }
}
