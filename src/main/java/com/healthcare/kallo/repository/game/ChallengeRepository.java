package com.healthcare.kallo.repository.game;

import com.healthcare.kallo.model.game.Challenge;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChallengeRepository extends JpaRepository<Challenge, UUID> {
  @Query("""
      select c from Challenge c
      left join fetch c.badge
      where c.active = true and c.startDate <= :date and c.endDate >= :date
      order by c.endDate asc, c.id asc
      """)
  List<Challenge> findJoinable(@Param("date") LocalDate date);

  @Query("""
      select c from Challenge c
      left join fetch c.badge
      where c.active = true
      order by c.endDate asc, c.id asc
      """)
  List<Challenge> findActive();
}
