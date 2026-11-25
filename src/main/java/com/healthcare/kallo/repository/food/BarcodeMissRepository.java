package com.healthcare.kallo.repository.food;

import com.healthcare.kallo.model.food.BarcodeMiss;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BarcodeMissRepository extends JpaRepository<BarcodeMiss, String> {

  @Query("SELECT b FROM BarcodeMiss b WHERE b.barcode = :barcode AND b.expiresAt > :now")
  Optional<BarcodeMiss> findActiveMiss(@Param("barcode") String barcode, @Param("now") Instant now);

  @Modifying
  @Query("DELETE FROM BarcodeMiss b WHERE b.expiresAt <= :now")
  void deleteExpired(@Param("now") Instant now);
}
