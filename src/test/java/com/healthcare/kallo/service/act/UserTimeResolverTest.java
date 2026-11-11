package com.healthcare.kallo.service.act;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.healthcare.kallo.model.auth.User;
import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.repository.auth.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserTimeResolverTest {
  @Mock UserRepository users;

  private UserTimeResolver resolver;
  private UUID userId;

  @BeforeEach
  void setUp() {
    resolver = new UserTimeResolver(users);
    userId = UUID.randomUUID();
  }

  @Test
  void resolvesLocalDayAcrossUtcBoundary() {
    User user = new User();
    user.setId(userId);
    user.setTimezone("Asia/Ho_Chi_Minh");
    when(users.findById(userId)).thenReturn(Optional.of(user));

    ZoneId zone = resolver.zoneOf(userId);
    UserTimeResolver.DayRange range = resolver.dayRange(LocalDate.of(2026, 11, 11), zone);

    assertThat(resolver.localDate(Instant.parse("2026-11-11T16:30:00Z"), zone))
        .isEqualTo(LocalDate.of(2026, 11, 11));
    assertThat(resolver.localDate(Instant.parse("2026-11-11T23:30:00Z"), zone))
        .isEqualTo(LocalDate.of(2026, 11, 12));
    assertThat(range.start()).isEqualTo(Instant.parse("2026-11-10T17:00:00Z"));
    assertThat(range.end()).isEqualTo(Instant.parse("2026-11-11T17:00:00Z"));
  }

  @Test
  void resolvesAnotherTimezone() {
    ZoneId zone = ZoneId.of("America/Los_Angeles");
    UserTimeResolver.DayRange range = resolver.dayRange(LocalDate.of(2026, 11, 11), zone);

    assertThat(range.start()).isEqualTo(Instant.parse("2026-11-11T08:00:00Z"));
    assertThat(range.end()).isEqualTo(Instant.parse("2026-11-12T08:00:00Z"));
    assertThat(resolver.localDate(Instant.parse("2026-11-12T07:30:00Z"), zone))
        .isEqualTo(LocalDate.of(2026, 11, 11));

    UserTimeResolver.DayRange dstRange = resolver.dayRange(LocalDate.of(2026, 11, 1), zone);
    assertThat(Duration.between(dstRange.start(), dstRange.end())).isEqualTo(Duration.ofHours(25));
  }

  @Test
  void rejectsUnknownUser() {
    when(users.findById(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> resolver.zoneOf(userId))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_FOUND);
  }
}
