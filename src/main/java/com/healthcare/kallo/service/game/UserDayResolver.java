package com.healthcare.kallo.service.game;

import com.healthcare.kallo.model.auth.User;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.DateTimeException;
import org.springframework.stereotype.Component;

@Component
public final class UserDayResolver {
  private final Clock clock;

  public UserDayResolver(Clock clock) {
    this.clock = clock;
  }

  public LocalDate today(User user) {
    return resolve(user.getTimezone(), clock.instant());
  }

  public LocalDate today(ZoneId zoneId) {
    return resolve(zoneId, clock.instant());
  }

  public LocalDate resolve(User user, Instant instant) {
    return resolve(user.getTimezone(), instant);
  }

  public LocalDate resolve(String timezone, Instant instant) {
    if (timezone == null || timezone.isBlank()) {
      throw new IllegalArgumentException("timezone must not be blank");
    }
    try {
      return resolve(ZoneId.of(timezone), instant);
    } catch (DateTimeException e) {
      throw new IllegalArgumentException("timezone must be a valid IANA timezone", e);
    }
  }

  public LocalDate resolve(ZoneId zoneId, Instant instant) {
    if (zoneId == null) throw new IllegalArgumentException("zoneId must not be null");
    if (instant == null) throw new IllegalArgumentException("instant must not be null");
    return instant.atZone(zoneId).toLocalDate();
  }
}
