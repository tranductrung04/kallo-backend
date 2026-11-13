package com.healthcare.kallo.repository.sys;

import com.healthcare.kallo.model.sys.NotificationSettings;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface NotificationSettingsRepository extends JpaRepository<NotificationSettings, UUID> {
  @Modifying
  @Query(value = "INSERT INTO notification_settings (user_id) VALUES (:userId) ON CONFLICT (user_id) DO NOTHING", nativeQuery = true)
  int insertDefaults(@Param("userId") UUID userId);
}
