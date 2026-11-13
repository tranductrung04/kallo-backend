package com.healthcare.kallo.service.sys.impl;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.config.SyncProperties;
import com.healthcare.kallo.dto.response.sys.SyncOperationResponse;
import com.healthcare.kallo.dto.response.sys.SyncPullResponse;
import com.healthcare.kallo.dto.response.sys.SyncPushResponse;
import com.healthcare.kallo.service.sys.SyncService;
import com.healthcare.kallo.service.sys.sync.SyncApplyResult;
import com.healthcare.kallo.service.sys.sync.SyncChange;
import com.healthcare.kallo.service.sys.sync.SyncEntityHandler;
import com.healthcare.kallo.service.sys.sync.SyncEntityType;
import com.healthcare.kallo.service.sys.sync.SyncForbiddenReferenceException;
import com.healthcare.kallo.service.sys.sync.SyncOperation;
import com.healthcare.kallo.service.sys.sync.SyncRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SyncServiceImpl implements SyncService {
  private static final String CURSOR_VERSION = "v1";
  private static final Logger log = LoggerFactory.getLogger(SyncServiceImpl.class);

  private final SyncProperties properties;
  private final SyncRegistry registry;
  private final TransactionTemplate transactionTemplate;
  private final Clock clock;

  public SyncServiceImpl(
      SyncProperties properties,
      SyncRegistry registry,
      PlatformTransactionManager transactionManager,
      Clock clock) {
    this.properties = properties;
    this.registry = registry;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.clock = clock;
  }

  @Override
  public SyncPushResponse push(UUID userId, List<SyncOperation> operations) {
    if (operations == null || operations.isEmpty() || operations.size() > properties.maxOperations()) {
      throw new BusinessException(ErrorCode.SYNC_BATCH_TOO_LARGE);
    }

    Instant now = Instant.now(clock);
    List<IndexedOperation> ordered = new ArrayList<>(operations.size());
    for (int index = 0; index < operations.size(); index++) {
      ordered.add(new IndexedOperation(index, operations.get(index)));
    }
    ordered.sort(
        Comparator.comparing(
            indexed -> indexed.operation() == null ? null : indexed.operation().updatedAt(),
            Comparator.nullsLast(Comparator.naturalOrder())));

    SyncOperationResponse[] results = new SyncOperationResponse[operations.size()];
    for (IndexedOperation indexed : ordered) {
      SyncOperation operation = indexed.operation();
      SyncOperation normalized = normalize(operation, now);
      SyncApplyResult result = applyIsolated(userId, normalized);
      results[indexed.index()] =
          SyncOperationResponse.from(
              normalized == null ? null : normalized.rawEntityType(),
              normalized == null ? null : normalized.clientUuid(),
              result);
    }
    return new SyncPushResponse(List.of(results), now);
  }

  private SyncApplyResult applyIsolated(UUID userId, SyncOperation operation) {
    SyncApplyResult invalid = validate(operation);
    if (invalid != null) return invalid;

    SyncEntityHandler handler = registry.find(operation.entityType());
    if (handler == null) return SyncApplyResult.rejected("UNKNOWN_ENTITY");

    try {
      SyncApplyResult result =
          transactionTemplate.execute(status -> handler.apply(userId, operation));
      return result == null ? SyncApplyResult.rejected("VALIDATION_ERROR") : result;
    } catch (IllegalArgumentException | BusinessException e) {
      return SyncApplyResult.rejected("VALIDATION_ERROR");
    } catch (SyncForbiddenReferenceException | AccessDeniedException e) {
      return SyncApplyResult.rejected("FORBIDDEN_REFERENCE");
    } catch (RuntimeException e) {
      log.error("Unexpected sync operation failure for entity {}", operation.entityType(), e);
      throw e;
    }
  }

  private SyncApplyResult validate(SyncOperation operation) {
    if (operation == null
        || operation.clientUuid() == null
        || operation.updatedAt() == null
        || operation.operation() == null) {
      return SyncApplyResult.rejected("VALIDATION_ERROR");
    }
    if (operation.entityType() == null) {
      return SyncApplyResult.rejected(
          operation.rawEntityType() == null ? "VALIDATION_ERROR" : "UNKNOWN_ENTITY");
    }
    return null;
  }

  private SyncOperation normalize(SyncOperation operation, Instant now) {
    if (operation == null || operation.updatedAt() == null) return operation;
    if (!operation.updatedAt().isAfter(now.plus(properties.maxFutureSkew()))) return operation;
    return new SyncOperation(
        operation.clientUuid(),
        operation.entityType(),
        operation.operation(),
        now,
        operation.data(),
        operation.rawEntityType());
  }

  @Override
  public SyncPullResponse pull(UUID userId, String cursor, Integer requestedLimit) {
    Instant now = Instant.now(clock);
    Cursor decoded = Cursor.decode(cursor, now);
    if (decoded.issuedAt().isBefore(now.minus(properties.tombstoneRetention()))) {
      throw new BusinessException(ErrorCode.FULL_RESYNC_REQUIRED);
    }

    int limit = requestedLimit == null ? properties.defaultPage() : requestedLimit;
    if (limit < 1 || limit > properties.maxPage()) {
      throw new BusinessException(ErrorCode.INVALID_PARAMETER);
    }

    Instant settledBefore = now.minus(properties.settleDelay());
    List<SyncChange> changes = new ArrayList<>();
    for (SyncEntityType type : SyncEntityType.values()) {
      SyncEntityHandler handler = registry.find(type);
      if (handler != null) {
        changes.addAll(handler.changesSince(userId, decoded.sequence(), settledBefore, limit + 1));
      }
    }
    changes.sort(Comparator.comparingLong(SyncChange::syncSeq));

    boolean hasMore = changes.size() > limit;
    if (hasMore) changes = new ArrayList<>(changes.subList(0, limit));
    long nextSequence =
        changes.isEmpty() ? decoded.sequence() : changes.get(changes.size() - 1).syncSeq();
    Instant issuedAt = hasMore ? decoded.issuedAt() : now;
    return new SyncPullResponse(
        changes, Cursor.encode(nextSequence, issuedAt), hasMore, now);
  }

  private record IndexedOperation(int index, SyncOperation operation) {}

  record Cursor(long sequence, Instant issuedAt) {
    static Cursor decode(String value, Instant now) {
      if (value == null || value.isBlank()) return new Cursor(0, now);
      try {
        String raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        String[] parts = raw.split(":", 3);
        if (parts.length != 3 || !CURSOR_VERSION.equals(parts[0])) {
          throw new IllegalArgumentException();
        }
        long sequence = Long.parseLong(parts[1]);
        Instant issuedAt = Instant.ofEpochSecond(Long.parseLong(parts[2]));
        if (sequence < 0 || issuedAt.isAfter(now)) throw new IllegalArgumentException();
        return new Cursor(sequence, issuedAt);
      } catch (RuntimeException e) {
        throw new BusinessException(ErrorCode.INVALID_PARAMETER);
      }
    }

    static String encode(long sequence, Instant issuedAt) {
      String raw = CURSOR_VERSION + ":" + sequence + ":" + issuedAt.getEpochSecond();
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
  }
}
