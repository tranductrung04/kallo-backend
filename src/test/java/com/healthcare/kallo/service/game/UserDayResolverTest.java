package com.healthcare.kallo.service.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.healthcare.kallo.model.auth.User;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class UserDayResolverTest {
  private static final Instant INSTANT = Instant.parse("2026-11-13T23:30:00Z");

  @Test
  void resolvesInstantInUserTimezone() {
    User user = new User();
    user.setTimezone("Asia/Ho_Chi_Minh");
    UserDayResolver resolver = resolver();

    assertThat(resolver.today(user)).hasToString("2026-11-14");
    assertThat(resolver.resolve(user, INSTANT)).hasToString("2026-11-14");
  }

  @Test
  void resolvesZoneIdWithoutUserEntity() {
    UserDayResolver resolver = resolver();

    assertThat(resolver.today(ZoneId.of("Pacific/Kiritimati"))).hasToString("2026-11-14");
    assertThat(resolver.resolve(ZoneId.of("America/New_York"), INSTANT)).hasToString("2026-11-13");
  }

  @Test
  void handlesDaylightSavingTime() {
    UserDayResolver resolver = resolver();

    assertThat(resolver.resolve("America/New_York", Instant.parse("2026-03-08T06:59:00Z")))
        .hasToString("2026-03-08");
    assertThat(resolver.resolve("America/New_York", Instant.parse("2026-03-09T03:59:00Z")))
        .hasToString("2026-03-08");
  }

  @Test
  void rejectsMissingOrInvalidTimezone() {
    User user = new User();
    user.setTimezone(null);

    assertThatThrownBy(() -> resolver().today(user))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> resolver().resolve("Not/AZone", INSTANT))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> resolver().resolve((ZoneId) null, INSTANT))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private UserDayResolver resolver() {
    return new UserDayResolver(Clock.fixed(INSTANT, ZoneOffset.UTC));
  }
}
