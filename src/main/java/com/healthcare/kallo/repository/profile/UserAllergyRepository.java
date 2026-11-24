package com.healthcare.kallo.repository.profile;

import com.healthcare.kallo.model.profile.UserAllergy;
import com.healthcare.kallo.model.profile.UserAllergyId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserAllergyRepository extends JpaRepository<UserAllergy, UserAllergyId> {

  List<UserAllergy> findAllByIdUserId(UUID userId);

  @Modifying
  @Query("DELETE FROM UserAllergy ua WHERE ua.id.userId = :userId")
  void deleteAllByUserId(@Param("userId") UUID userId);
}
