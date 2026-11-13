package com.healthcare.kallo.repository.game;

import com.healthcare.kallo.model.game.UserGameSettings;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface UserGameSettingsRepository extends JpaRepository<UserGameSettings, UUID> {
  Optional<UserGameSettings> findByFriendCode(String friendCode);

  @Modifying
  @Transactional
  @Query(value = """
      INSERT INTO user_game_settings (user_id, friend_code)
      VALUES (:userId, :friendCode)
      ON CONFLICT DO NOTHING
      """, nativeQuery = true)
  int insertIfAbsent(@Param("userId") UUID userId, @Param("friendCode") String friendCode);
}
