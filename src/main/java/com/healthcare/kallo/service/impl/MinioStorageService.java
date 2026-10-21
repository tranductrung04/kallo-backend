package com.healthcare.kallo.service.impl;

import com.healthcare.kallo.config.StorageProperties;
import com.healthcare.kallo.service.StorageService;
import io.minio.*;
import io.minio.http.Method;
import io.minio.messages.Item;
import java.io.InputStream;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;

@Service
public class MinioStorageService implements StorageService {
  private final MinioClient client;
  private final MinioClient presigningClient;
  private final StorageProperties properties;

  public MinioStorageService(
      @Qualifier("storageClient") MinioClient client,
      @Qualifier("presigningClient") MinioClient presigningClient,
      StorageProperties properties) {
    this.client = client;
    this.presigningClient = presigningClient;
    this.properties = properties;
  }

  @Override
  public void upload(String objectKey, InputStream content, long size, String contentType) {
    try {
      boolean bucketExists =
          client.bucketExists(
              BucketExistsArgs.builder().bucket(properties.bucket()).build());
      if (!bucketExists) {
        client.makeBucket(MakeBucketArgs.builder().bucket(properties.bucket()).build());
      }
      client.putObject(
          PutObjectArgs.builder()
              .bucket(properties.bucket())
              .object(objectKey)
              .stream(content, size, -1)
              .contentType(contentType)
              .build());
    } catch (Exception e) {
      throw new IllegalStateException("Unable to upload object", e);
    }
  }

  @Override
  public void delete(String objectKey) {
    try {
      client.removeObject(
          RemoveObjectArgs.builder().bucket(properties.bucket()).object(objectKey).build());
    } catch (Exception e) {
      throw new IllegalStateException("Unable to delete object", e);
    }
  }

  @Override
  public void deletePrefix(String prefix) {
    try {
      Iterable<Result<Item>> objects =
          client.listObjects(
              ListObjectsArgs.builder()
                  .bucket(properties.bucket())
                  .prefix(prefix)
                  .recursive(true)
                  .build());
      for (Result<Item> result : objects) {
        client.removeObject(
            RemoveObjectArgs.builder()
                .bucket(properties.bucket())
                .object(result.get().objectName())
                .build());
      }
    } catch (Exception e) {
      throw new IllegalStateException("Unable to delete objects", e);
    }
  }

  @Override
  public String presignedUrl(String objectKey, Duration expiration) {
    try {
      return presigningClient.getPresignedObjectUrl(
          GetPresignedObjectUrlArgs.builder()
              .method(Method.GET)
              .bucket(properties.bucket())
              .object(objectKey)
              .expiry((int) Math.min(expiration.toSeconds(), 7 * 24 * 60 * 60), TimeUnit.SECONDS)
              .build());
    } catch (Exception e) {
      throw new IllegalStateException("Unable to create object URL", e);
    }
  }
}
