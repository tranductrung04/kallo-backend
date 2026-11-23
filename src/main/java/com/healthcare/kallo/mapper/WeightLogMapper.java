package com.healthcare.kallo.mapper;

import com.healthcare.kallo.dto.response.act.WeightLogResponse;
import com.healthcare.kallo.model.act.WeightLog;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import java.time.ZoneId;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)

public interface WeightLogMapper {
  @org.mapstruct.BeanMapping(ignoreByDefault = true)
  @Mapping(target = "id", source = "weightLog.id")
  @Mapping(target = "userId", source = "weightLog.user.id")
  @Mapping(target = "clientUuid", source = "weightLog.clientUuid")
  @Mapping(target = "weightKg", source = "weightLog.weightKg")
  @Mapping(target = "loggedAt", source = "weightLog.loggedAt")
  @Mapping(target = "localDate", expression = "java(weightLog.getLoggedAt().atZone(zone).toLocalDate())")
  @Mapping(target = "note", source = "weightLog.note")
  @Mapping(target = "createdAt", source = "weightLog.createdAt")
  @Mapping(target = "updatedAt", source = "weightLog.updatedAt")
  @Mapping(target = "deletedAt", source = "weightLog.deletedAt")
  WeightLogResponse toResponse(WeightLog weightLog, ZoneId zone);
}
