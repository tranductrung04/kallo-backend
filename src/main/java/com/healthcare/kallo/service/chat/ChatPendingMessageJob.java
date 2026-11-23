package com.healthcare.kallo.service.chat;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ChatPendingMessageJob {
  private final ChatTxService tx;

  public ChatPendingMessageJob(ChatTxService tx) {
    this.tx = tx;
  }

  @Scheduled(fixedDelay = 60_000)
  public void failExpiredMessages() {
    tx.failExpiredPendingMessages();
  }
}
