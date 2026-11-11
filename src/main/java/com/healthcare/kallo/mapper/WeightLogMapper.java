package com.healthcare.kallo.mapper;

import com.healthcare.kallo.dto.response.act.WeightLogResponse;
import com.healthcare.kallo.model.act.WeightLog;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface WeightLogMapper {
  @Mapping(target = "userId", source = "user.id")
  WeightLogResponse toResponse(WeightLog weightLog);
}
