package com.healthcare.kallo.repository.act;

import com.healthcare.kallo.model.act.ActivityType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityTypeRepository extends JpaRepository<ActivityType, String> {
  Optional<ActivityType> findByCodeAndActiveTrue(String code);
  List<ActivityType> findByActiveTrueOrderBySortOrderAscCodeAsc();
}
