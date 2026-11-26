package com.healthcare.kallo.repository.nutri;

import com.healthcare.kallo.model.nutri.DailySummary;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DailySummaryRepository extends JpaRepository<DailySummary, UUID> {

  Optional<DailySummary> findByUserIdAndSummaryDate(UUID userId, LocalDate summaryDate);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
      SELECT d FROM DailySummary d
      WHERE d.userId = :userId AND d.summaryDate = :summaryDate
      """)
  Optional<DailySummary> findByUserIdAndSummaryDateForUpdate(
      @Param("userId") UUID userId,
      @Param("summaryDate") LocalDate summaryDate);

  @Modifying
  @Query(value = """
      INSERT INTO daily_summaries (user_id, summary_date, created_at, updated_at)
      VALUES (:userId, :summaryDate, NOW(), NOW())
      ON CONFLICT (user_id, summary_date) DO NOTHING
      """, nativeQuery = true)
  int insertIfAbsent(
      @Param("userId") UUID userId,
      @Param("summaryDate") LocalDate summaryDate);

  List<DailySummary> findByUserIdAndSummaryDateBetweenOrderBySummaryDateAsc(
      UUID userId, LocalDate fromInclusive, LocalDate toInclusive);

  @Query("""
      SELECT COALESCE(SUM(d.steps), 0)
      FROM DailySummary d
      WHERE d.userId = :userId
        AND d.summaryDate >= :fromInclusive
        AND d.summaryDate <= :toInclusive
      """)
  long sumStepsInRange(
      @Param("userId") UUID userId,
      @Param("fromInclusive") LocalDate fromInclusive,
      @Param("toInclusive") LocalDate toInclusive);
}
