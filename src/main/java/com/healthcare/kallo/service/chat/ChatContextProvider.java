package com.healthcare.kallo.service.chat;

import java.util.UUID;

public interface ChatContextProvider {
  ChatContextData get(UUID userId);
}
