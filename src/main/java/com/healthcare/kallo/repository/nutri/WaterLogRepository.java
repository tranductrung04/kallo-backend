package com.healthcare.kallo.repository.nutri;

import com.healthcare.kallo.model.nutri.WaterLog;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WaterLogRepository extends JpaRepository<WaterLog, UUID> {

  Optional<WaterLog> findByUserIdAndClientUuid(UUID userId, UUID clientUuid);

  Optional<WaterLog> findByIdAndUserId(UUID id, UUID userId);

  List<WaterLog> findByUserIdAndLoggedDateOrderByLoggedAtAsc(UUID userId, LocalDate loggedDate);

  @Query("""
      SELECT COALESCE(SUM(w.amountMl), 0)
      FROM WaterLog w
      WHERE w.user.id = :userId AND w.loggedDate = :date
      """)
  int sumWaterMlByDate(@Param("userId") UUID userId, @Param("date") LocalDate date);

  Optional<WaterLog> findTopByUserIdOrderByLoggedAtDesc(UUID userId);
}
