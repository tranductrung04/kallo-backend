package com.healthcare.kallo.service.sys.sync;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SyncRegistry {
  private final Map<SyncEntityType, SyncEntityHandler> handlers;

  public SyncRegistry(List<SyncEntityHandler> beans) {
    EnumMap<SyncEntityType, SyncEntityHandler> registry = new EnumMap<>(SyncEntityType.class);
    for (SyncEntityHandler handler : beans) {
      if (registry.put(handler.type(), handler) != null) {
        throw new IllegalStateException("Duplicate sync handler for " + handler.type());
      }
    }
    handlers = Map.copyOf(registry);
  }

  public SyncEntityHandler find(SyncEntityType type) {
    return handlers.get(type);
  }
}
