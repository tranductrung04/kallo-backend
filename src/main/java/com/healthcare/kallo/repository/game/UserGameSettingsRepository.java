package com.healthcare.kallo.repository.game;

import com.healthcare.kallo.model.auth.User;
import com.healthcare.kallo.model.game.UserGameSettings;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface UserGameSettingsRepository extends JpaRepository<UserGameSettings, UUID> {
  Optional<UserGameSettings> findByFriendCode(String friendCode);

  @Query("select u from User u join UserGameSettings s on s.userId = u.id where u.id <> :userId and u.status = com.healthcare.kallo.model.auth.enums.UserStatus.ACTIVE and u.emailVerified = true and s.friendCode = :friendCode")
  Optional<User> findActiveVerifiedByFriendCode(@Param("userId") UUID userId, @Param("friendCode") String friendCode);

  @Query("select u from User u join UserGameSettings s on s.userId = u.id where u.id <> :userId and u.status = com.healthcare.kallo.model.auth.enums.UserStatus.ACTIVE and u.emailVerified = true and s.discoverable = true and lower(u.displayName) like :prefix escape '\\' order by lower(u.displayName), u.id")
  List<User> searchDiscoverable(@Param("userId") UUID userId, @Param("prefix") String prefix, Pageable pageable);

  @Modifying
  @Transactional
  @Query(value = """
      INSERT INTO user_game_settings (user_id, friend_code)
      VALUES (:userId, :friendCode)
      ON CONFLICT DO NOTHING
      """, nativeQuery = true)
  int insertIfAbsent(@Param("userId") UUID userId, @Param("friendCode") String friendCode);
}
