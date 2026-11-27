package com.healthcare.kallo.service.chat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChatSummaryServiceTest {

  @Test
  void skipsSummarizationWhenBelowBatchThreshold() {
    ChatTxService tx = mock(ChatTxService.class);
    ChatAiClient ai = mock(ChatAiClient.class);
    ChatSummaryService service = new ChatSummaryService(tx, ai);
    UUID conversationId = UUID.randomUUID();

    when(tx.loadSummarySnapshot(conversationId, 12, ChatSummaryService.SUMMARY_BATCH_SIZE)).thenReturn(null);

    service.summarizeIfNeeded(conversationId, 12);

    verify(ai, never()).summarize(any(), any());
    verify(tx, never()).saveSummaryWithWatermark(any(), any(), any(), any());
  }

  @Test
  void summarizesIncrementalSliceAndUpdatesWatermarkAtomically() {
    ChatTxService tx = mock(ChatTxService.class);
    ChatAiClient ai = mock(ChatAiClient.class);
    ChatSummaryService service = new ChatSummaryService(tx, ai);
    UUID conversationId = UUID.randomUUID();
    UUID prevWatermark = UUID.randomUUID();
    UUID newWatermark = UUID.randomUUID();

    var slice = List.of(new ChatAiRequest.ChatHistoryMessage("user", "Tôi thích ăn phở"));
    when(tx.loadSummarySnapshot(eq(conversationId), eq(12), anyInt()))
        .thenReturn(new ChatTxService.SummarySnapshot("Tóm tắt cũ", prevWatermark, newWatermark, slice));
    when(ai.summarize("Tóm tắt cũ", slice)).thenReturn("Tóm tắt mới");
    when(tx.saveSummaryWithWatermark(conversationId, prevWatermark, newWatermark, "Tóm tắt mới")).thenReturn(true);

    service.summarizeIfNeeded(conversationId, 12);

    verify(tx).saveSummaryWithWatermark(conversationId, prevWatermark, newWatermark, "Tóm tắt mới");
  }
}
