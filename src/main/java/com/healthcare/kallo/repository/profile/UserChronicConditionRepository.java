package com.healthcare.kallo.repository.profile;

import com.healthcare.kallo.model.profile.UserChronicCondition;
import com.healthcare.kallo.model.profile.UserChronicConditionId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserChronicConditionRepository extends JpaRepository<UserChronicCondition, UserChronicConditionId> {

  @Query("SELECT ucc FROM UserChronicCondition ucc JOIN FETCH ucc.condition WHERE ucc.id.userId = :userId ORDER BY ucc.condition.name ASC")
  List<UserChronicCondition> findAllByUserIdWithCondition(@Param("userId") UUID userId);

  @Modifying
  @Query("DELETE FROM UserChronicCondition ucc WHERE ucc.id.userId = :userId")
  void deleteAllByUserId(@Param("userId") UUID userId);
}
