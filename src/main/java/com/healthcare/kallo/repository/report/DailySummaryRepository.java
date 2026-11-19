package com.healthcare.kallo.repository.report;

import com.healthcare.kallo.model.report.DailySummary;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DailySummaryRepository extends JpaRepository<DailySummary, UUID> {

  Optional<DailySummary> findByUserIdAndSummaryDate(UUID userId, LocalDate summaryDate);

  List<DailySummary> findByUserIdAndSummaryDateBetweenOrderBySummaryDateAsc(
      UUID userId, LocalDate startDate, LocalDate endDate);

  @Query("""
      SELECT COALESCE(SUM(d.steps), 0)
      FROM DailySummary d
      WHERE d.user.id = :userId AND d.summaryDate BETWEEN :start AND :end
      """)
  long sumStepsBetween(
      @Param("userId") UUID userId,
      @Param("start") LocalDate start,
      @Param("end") LocalDate end);
}
