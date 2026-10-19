package com.healthcare.kallo.mapper;

import com.healthcare.kallo.dto.response.UserSummary;
import com.healthcare.kallo.model.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {
  @Mapping(target = "role", expression = "java(user.getRole().name())")
  UserSummary toSummary(User user);
}
