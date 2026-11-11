package com.healthcare.kallo.service.act;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.model.auth.User;
import com.healthcare.kallo.repository.auth.UserRepository;
import java.time.Instant;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class UserTimeResolver {
  private final UserRepository users;
  private final Clock clock;

  public UserTimeResolver(UserRepository users) {
    this(users, Clock.systemUTC());
  }

  @Autowired
  public UserTimeResolver(UserRepository users, Clock clock) {
    this.users = users;
    this.clock = clock;
  }

  public ZoneId zoneOf(UUID userId) {
    User user =
        users.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    return ZoneId.of(user.getTimezone());
  }

  public DayRange dayRange(LocalDate date, ZoneId zone) {
    Instant start = date.atStartOfDay(zone).toInstant();
    return new DayRange(start, date.plusDays(1).atStartOfDay(zone).toInstant());
  }

  public LocalDate localDate(Instant instant, ZoneId zone) {
    return instant.atZone(zone).toLocalDate();
  }

  public record DayRange(Instant start, Instant end) {}
}
