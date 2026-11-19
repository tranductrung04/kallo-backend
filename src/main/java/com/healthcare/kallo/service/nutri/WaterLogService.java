package com.healthcare.kallo.service.nutri;

import com.healthcare.kallo.dto.request.nutri.CreateWaterLogRequest;
import com.healthcare.kallo.dto.response.nutri.DailyWaterResponse;
import com.healthcare.kallo.dto.response.nutri.WaterLogResponse;
import java.time.LocalDate;
import java.util.UUID;

public interface WaterLogService {

  WaterLogResponse logWater(UUID userId, CreateWaterLogRequest request);

  void deleteWaterLog(UUID userId, UUID logId);

  DailyWaterResponse getDailyWater(UUID userId, LocalDate date);
}
