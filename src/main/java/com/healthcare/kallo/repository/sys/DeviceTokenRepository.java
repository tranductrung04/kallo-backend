package com.healthcare.kallo.repository.sys;

import com.healthcare.kallo.model.sys.DeviceToken;
import com.healthcare.kallo.model.sys.enums.DevicePlatform;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, UUID> {
  Optional<DeviceToken> findByToken(String token);
  List<DeviceToken> findByUserIdOrderByLastSeenAtAsc(UUID userId);
  long countByUserId(UUID userId);
  void deleteByIdAndUserId(UUID id, UUID userId);
  void deleteByToken(String token);

  @org.springframework.data.jpa.repository.Modifying
  @org.springframework.data.jpa.repository.Query("delete from DeviceToken d where d.lastSeenAt < :cutoff")
  int deleteLastSeenBefore(@org.springframework.data.repository.query.Param("cutoff") Instant cutoff);

  @org.springframework.data.jpa.repository.Modifying
  @org.springframework.data.jpa.repository.Query(
      value =
          """
          INSERT INTO device_tokens (user_id, token, platform, locale, app_version, last_seen_at)
          VALUES (:userId, :token, :platform, :locale, :appVersion, :lastSeenAt)
          ON CONFLICT (token) DO UPDATE SET
            user_id = EXCLUDED.user_id,
            platform = EXCLUDED.platform,
            locale = EXCLUDED.locale,
            app_version = EXCLUDED.app_version,
            last_seen_at = EXCLUDED.last_seen_at
          """,
      nativeQuery = true)
  void upsert(
      @org.springframework.data.repository.query.Param("userId") UUID userId,
      @org.springframework.data.repository.query.Param("token") String token,
      @org.springframework.data.repository.query.Param("platform") String platform,
      @org.springframework.data.repository.query.Param("locale") String locale,
      @org.springframework.data.repository.query.Param("appVersion") String appVersion,
      @org.springframework.data.repository.query.Param("lastSeenAt") Instant lastSeenAt);
}
