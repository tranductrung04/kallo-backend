package com.healthcare.kallo.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.FileInputStream;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "app.push.enabled", havingValue = "true")
public class FirebaseConfig {
  @Bean
  FirebaseMessaging firebaseMessaging(PushProperties properties) {
    try (FileInputStream credentials = new FileInputStream(properties.credentialsPath())) {
      FirebaseOptions options =
          FirebaseOptions.builder()
              .setCredentials(GoogleCredentials.fromStream(credentials))
              .build();
      FirebaseApp app =
          FirebaseApp.getApps().stream()
              .findFirst()
              .orElseGet(() -> FirebaseApp.initializeApp(options));
      return FirebaseMessaging.getInstance(app);
    } catch (IOException e) {
      throw new IllegalStateException(
          "Unable to initialize Firebase credentials from " + properties.credentialsPath(), e);
    }
  }
}
