package com.healthcare.kallo.config;

import io.minio.MinioClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {
  @Bean
  @Qualifier("storageClient")
  MinioClient minioClient(StorageProperties properties) {
    return MinioClient.builder()
        .endpoint(properties.endpoint())
        .credentials(properties.accessKey(), properties.secretKey())
        .region("us-east-1")
        .build();
  }

  @Bean
  @Qualifier("presigningClient")
  MinioClient presigningClient(StorageProperties properties) {
    return MinioClient.builder()
        .endpoint(properties.publicEndpoint())
        .credentials(properties.accessKey(), properties.secretKey())
        .region("us-east-1")
        .build();
  }
}
