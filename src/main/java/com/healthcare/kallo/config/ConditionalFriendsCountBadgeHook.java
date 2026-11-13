package com.healthcare.kallo.config;

import com.healthcare.kallo.service.game.FriendsCountBadgeHook;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConditionalFriendsCountBadgeHook {
  @Bean
  @ConditionalOnMissingBean(FriendsCountBadgeHook.class)
  FriendsCountBadgeHook friendsCountBadgeHook() {
    return new FriendsCountBadgeHook() {
      @Override
      public void onAccepted(UUID userId) {}
    };
  }
}
