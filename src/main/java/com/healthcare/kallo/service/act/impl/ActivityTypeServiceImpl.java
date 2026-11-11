package com.healthcare.kallo.service.act.impl;

import com.healthcare.kallo.dto.response.act.ActivityTypeResponse;
import com.healthcare.kallo.mapper.ActivityMapper;
import com.healthcare.kallo.repository.act.ActivityTypeRepository;
import com.healthcare.kallo.service.act.ActivityTypeService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivityTypeServiceImpl implements ActivityTypeService {
  private final ActivityTypeRepository types;
  private final ActivityMapper mapper;

  public ActivityTypeServiceImpl(ActivityTypeRepository types, ActivityMapper mapper) {
    this.types = types;
    this.mapper = mapper;
  }

  @Override
  @Transactional(readOnly = true)
  public List<ActivityTypeResponse> listActive() {
    return types.findByActiveTrueOrderBySortOrderAscCodeAsc().stream().map(mapper::toTypeResponse).toList();
  }
}
