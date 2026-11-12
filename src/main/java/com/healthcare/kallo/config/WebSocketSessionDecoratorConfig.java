package com.healthcare.kallo.config;

import com.healthcare.kallo.service.sys.WebSocketSessionRegistry;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

@Configuration
public class WebSocketSessionDecoratorConfig implements WebSocketMessageBrokerConfigurer {
  private final WebSocketSessionRegistry sessions;

  public WebSocketSessionDecoratorConfig(WebSocketSessionRegistry sessions) {
    this.sessions = sessions;
  }

  @Override
  public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
    registration
        .setMessageSizeLimit(64 * 1024)
        .setSendBufferSizeLimit(256 * 1024)
        .addDecoratorFactory(this::decorate);
  }

  private WebSocketHandler decorate(WebSocketHandler handler) {
    return new WebSocketHandlerDecorator(handler) {
      @Override
      public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        super.afterConnectionEstablished(session);
        sessions.attach(session.getId(), session);
      }

      @Override
      public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status)
          throws Exception {
        sessions.unregister(session.getId());
        super.afterConnectionClosed(session, status);
      }
    };
  }
}
