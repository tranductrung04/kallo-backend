package com.healthcare.kallo.mapper;

import com.healthcare.kallo.dto.response.act.*;
import com.healthcare.kallo.model.act.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ActivityMapper {
  @Mapping(target = "userId", source = "user.id")
  @Mapping(target = "activityType", source = "activityType.code")
  @Mapping(target = "supersededBy", source = "supersededBy.id")
  ActivityResponse toResponse(ActivityLog activity);
  ActivityTypeResponse toTypeResponse(ActivityType type);
}
