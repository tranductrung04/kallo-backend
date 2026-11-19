package com.healthcare.kallo.repository.food;

import com.healthcare.kallo.model.food.BarcodeMiss;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BarcodeMissRepository extends JpaRepository<BarcodeMiss, String> {

  Optional<BarcodeMiss> findByBarcodeAndExpiresAtAfter(String barcode, Instant now);

  void deleteByExpiresAtBefore(Instant now);
}
