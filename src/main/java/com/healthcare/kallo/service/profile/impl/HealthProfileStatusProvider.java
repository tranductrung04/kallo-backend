package com.healthcare.kallo.service.profile.impl;

import com.healthcare.kallo.model.auth.User;
import com.healthcare.kallo.repository.profile.UserHealthProfileRepository;
import com.healthcare.kallo.service.auth.ProfileStatusProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HealthProfileStatusProvider implements ProfileStatusProvider {

  private final UserHealthProfileRepository healthProfileRepository;

  @Override
  public boolean isCompleted(User user) {
    if (user == null || user.getId() == null) {
      return false;
    }
    return healthProfileRepository.existsById(user.getId());
  }
}
