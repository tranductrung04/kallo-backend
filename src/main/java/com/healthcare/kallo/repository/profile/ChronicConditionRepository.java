package com.healthcare.kallo.repository.profile;

import com.healthcare.kallo.model.profile.ChronicCondition;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChronicConditionRepository extends JpaRepository<ChronicCondition, UUID> {
  List<ChronicCondition> findAllByIsActiveTrueOrderByNameAsc();

  List<ChronicCondition> findAllByIdIn(Collection<UUID> ids);

  Optional<ChronicCondition> findByCode(String code);
}
