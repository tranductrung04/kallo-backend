package com.healthcare.kallo.service.chat;

public class ChatSseException extends RuntimeException {
  private final String code;
  private final boolean clientDisconnected;

  public ChatSseException(Throwable cause) {
    this(cause, "CHAT_STREAM_FAILED", false);
  }

  public ChatSseException(String code, String message) {
    super(message);
    this.code = code;
    this.clientDisconnected = false;
  }

  private ChatSseException(Throwable cause, String code, boolean clientDisconnected) {
    super(cause);
    this.code = code;
    this.clientDisconnected = clientDisconnected;
  }

  public static ChatSseException clientDisconnected(Throwable cause) {
    return new ChatSseException(cause, "CLIENT_DISCONNECTED", true);
  }

  public String code() {
    return code;
  }

  public boolean clientDisconnected() {
    return clientDisconnected;
  }
}
