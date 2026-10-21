package com.healthcare.kallo.service;

import java.io.InputStream;
import java.time.Duration;

public interface StorageService {
  void upload(String objectKey, InputStream content, long size, String contentType);

  void delete(String objectKey);

  void deletePrefix(String prefix);

  String presignedUrl(String objectKey, Duration expiration);
}
