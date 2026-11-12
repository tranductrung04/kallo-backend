package com.healthcare.kallo.config;

import com.healthcare.kallo.security.sys.StompAuthChannelInterceptor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
  private final WebSocketProperties properties;
  private final StompAuthChannelInterceptor authInterceptor;
  private final ThreadPoolTaskScheduler brokerTaskScheduler;

  public WebSocketConfig(
      WebSocketProperties properties,
      StompAuthChannelInterceptor authInterceptor,
      @Qualifier("brokerTaskScheduler") ThreadPoolTaskScheduler brokerTaskScheduler) {
    this.properties = properties;
    this.authInterceptor = authInterceptor;
    this.brokerTaskScheduler = brokerTaskScheduler;
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry
        .enableSimpleBroker("/queue")
        .setHeartbeatValue(new long[] {10_000, 10_000})
        .setTaskScheduler(brokerTaskScheduler);
    registry.setApplicationDestinationPrefixes("/app");
    registry.setUserDestinationPrefix("/user");
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/ws")
        .setAllowedOriginPatterns(properties.allowedOrigins().toArray(String[]::new));
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(authInterceptor);
  }
}
