package com.healthcare.kallo.repository.act;

import com.healthcare.kallo.model.act.HealthSyncLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HealthSyncLogRepository extends JpaRepository<HealthSyncLog, UUID> {}
