package com.healthcare.kallo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class KalloBackendApplication {

  public static void main(String[] args) {
    SpringApplication.run(KalloBackendApplication.class, args);
  }
}
