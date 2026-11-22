package com.healthcare.kallo.mapper;

import com.healthcare.kallo.dto.response.act.*;
import com.healthcare.kallo.model.act.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import java.time.ZoneId;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ActivityMapper {
  @org.mapstruct.BeanMapping(ignoreByDefault = true)
  @Mapping(target = "id", source = "activity.id")
  @Mapping(target = "userId", source = "activity.user.id")
  @Mapping(target = "clientUuid", source = "activity.clientUuid")
  @Mapping(target = "activityType", source = "activity.activityType.code")
  @Mapping(target = "nameVi", expression = "java(activity.getCustomName() != null ? activity.getCustomName() : activity.getActivityType().getNameVi())")
  @Mapping(target = "category", source = "activity.activityType.category")
  @Mapping(target = "localDate", expression = "java(activity.getStartedAt().atZone(zone).toLocalDate())")
  @Mapping(target = "intensity", source = "activity.intensity")
  @Mapping(target = "met", source = "activity.met")
  @Mapping(target = "weightKgUsed", source = "activity.weightKgUsed")
  @Mapping(target = "durationMin", source = "activity.durationMin")
  @Mapping(target = "distanceKm", source = "activity.distanceKm")
  @Mapping(target = "caloriesBurned", source = "activity.caloriesBurned")
  @Mapping(target = "source", source = "activity.source")
  @Mapping(target = "startedAt", source = "activity.startedAt")
  @Mapping(target = "endedAt", source = "activity.endedAt")
  @Mapping(target = "supersededBy", source = "activity.supersededBy.id")
  @Mapping(target = "note", source = "activity.note")
  @Mapping(target = "createdAt", source = "activity.createdAt")
  @Mapping(target = "updatedAt", source = "activity.updatedAt")
  @Mapping(target = "deletedAt", source = "activity.deletedAt")
  @Mapping(target = "routePolyline", source = "activity.routePolyline")
  ActivityResponse toResponse(ActivityLog activity, ZoneId zone);
  ActivityTypeResponse toTypeResponse(ActivityType type);
}
