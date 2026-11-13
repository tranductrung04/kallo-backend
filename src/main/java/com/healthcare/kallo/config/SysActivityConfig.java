package com.healthcare.kallo.config;

import com.healthcare.kallo.service.sys.NoopUserActivityPort;
import com.healthcare.kallo.service.sys.UserActivityPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Fallback;

@Configuration
public class SysActivityConfig {
  @Bean
  @Fallback
  UserActivityPort userActivityPort() {
    return new NoopUserActivityPort();
  }
}
