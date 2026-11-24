package com.healthcare.kallo.repository.profile;

import com.healthcare.kallo.model.profile.UserHealthProfile;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserHealthProfileRepository extends JpaRepository<UserHealthProfile, UUID> {}
