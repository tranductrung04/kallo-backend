package com.healthcare.kallo.service.chat;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class EmptyChatContextProvider implements ChatContextProvider {
  @Override
  public ChatContextData get(UUID userId) {
    return ChatContextData.empty();
  }
}
