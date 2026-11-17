package com.healthcare.kallo.service.profile.impl;

import com.healthcare.kallo.model.auth.User;
import com.healthcare.kallo.repository.profile.UserHealthProfileRepository;
import com.healthcare.kallo.service.auth.ProfileStatusProvider;
import org.springframework.stereotype.Component;

@Component
public class ProfileStatusProviderImpl implements ProfileStatusProvider {

  private final UserHealthProfileRepository profileRepository;

  public ProfileStatusProviderImpl(UserHealthProfileRepository profileRepository) {
    this.profileRepository = profileRepository;
  }

  @Override
  public boolean isCompleted(User user) {
    if (user == null || user.getId() == null) {
      return false;
    }
    return profileRepository.existsByUserId(user.getId());
  }
}
