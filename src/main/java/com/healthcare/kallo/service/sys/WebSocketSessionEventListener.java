package com.healthcare.kallo.service.sys;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class WebSocketSessionEventListener {
  private final WebSocketSessionRegistry sessions;

  public WebSocketSessionEventListener(WebSocketSessionRegistry sessions) {
    this.sessions = sessions;
  }

  @EventListener
  public void onDisconnect(SessionDisconnectEvent event) {
    sessions.unregister(event);
  }
}
