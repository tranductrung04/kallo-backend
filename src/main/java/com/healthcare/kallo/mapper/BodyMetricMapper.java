package com.healthcare.kallo.mapper;

import com.healthcare.kallo.dto.response.act.BodyMetricResponse;
import com.healthcare.kallo.model.act.BodyMetric;
import java.time.ZoneId;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface BodyMetricMapper {
  @BeanMapping(ignoreByDefault = true)
  @Mapping(target = "id", source = "metric.id")
  @Mapping(target = "userId", source = "metric.user.id")
  @Mapping(target = "clientUuid", source = "metric.clientUuid")
  @Mapping(target = "waistCm", source = "metric.waistCm")
  @Mapping(target = "hipCm", source = "metric.hipCm")
  @Mapping(target = "loggedAt", source = "metric.loggedAt")
  @Mapping(target = "localDate", expression = "java(metric.getLoggedAt().atZone(zone).toLocalDate())")
  @Mapping(target = "whr", expression = "java(metric.getWaistCm().divide(metric.getHipCm(), 2, java.math.RoundingMode.HALF_UP))")
  @Mapping(target = "riskLevel", expression = "java(metric.getWaistCm().divide(metric.getHipCm(), 2, java.math.RoundingMode.HALF_UP).compareTo(new java.math.BigDecimal(0.90)) >= 0 ? \"HIGH\" : metric.getWaistCm().divide(metric.getHipCm(), 2, java.math.RoundingMode.HALF_UP).compareTo(new java.math.BigDecimal(0.85)) >= 0 ? \"MODERATE\" : \"LOW\")")
  @Mapping(target = "note", source = "metric.note")
  @Mapping(target = "createdAt", source = "metric.createdAt")
  @Mapping(target = "updatedAt", source = "metric.updatedAt")
  @Mapping(target = "deletedAt", source = "metric.deletedAt")
  BodyMetricResponse toResponse(BodyMetric metric, ZoneId zone);
}
