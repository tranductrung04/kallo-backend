package com.healthcare.kallo.repository.game;

import com.healthcare.kallo.model.game.Streak;
import java.time.LocalDate;
import java.util.UUID;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface StreakRepository extends JpaRepository<Streak, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from Streak s where s.userId = :userId")
  Optional<Streak> findByUserIdForUpdate(@Param("userId") UUID userId);

  Slice<Streak> findByCurrentStreakGreaterThan(int value, Pageable pageable);

  @Modifying
  @Transactional
  @Query(value = """
      UPDATE streaks s SET current_streak = 0, updated_at = now()
      FROM users u
      WHERE u.id = s.user_id
        AND s.current_streak > 0
        AND s.last_completed_date IS NOT NULL
        AND s.last_completed_date < ((now() AT TIME ZONE u.timezone)::date - 1)
      """, nativeQuery = true)
  int resetExpiredCurrentStreaks();

  @Modifying
  @Transactional
  @Query(value = """
      INSERT INTO streaks (user_id, current_streak, longest_streak, last_completed_date, updated_at)
      VALUES (:userId, :currentStreak, :longestStreak, CAST(:lastCompletedDate AS date), now())
      ON CONFLICT (user_id) DO UPDATE SET
        current_streak = EXCLUDED.current_streak,
        longest_streak = EXCLUDED.longest_streak,
        last_completed_date = EXCLUDED.last_completed_date,
        updated_at = now()
      """, nativeQuery = true)
  int upsert(@Param("userId") UUID userId, @Param("currentStreak") int currentStreak,
      @Param("longestStreak") int longestStreak, @Param("lastCompletedDate") LocalDate lastCompletedDate);
}
