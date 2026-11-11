package com.healthcare.kallo.service.act;

import com.healthcare.kallo.dto.response.act.ActivityTypeResponse;
import java.util.List;

public interface ActivityTypeService {
  List<ActivityTypeResponse> listActive();
}
