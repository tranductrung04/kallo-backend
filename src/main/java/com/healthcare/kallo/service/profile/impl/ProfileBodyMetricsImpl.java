package com.healthcare.kallo.service.profile.impl;

import com.healthcare.kallo.model.profile.UserHealthProfile;
import com.healthcare.kallo.repository.profile.UserHealthProfileRepository;
import com.healthcare.kallo.service.act.ProfileBodyMetrics;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ProfileBodyMetricsImpl implements ProfileBodyMetrics {

  private final UserHealthProfileRepository profileRepository;

  public ProfileBodyMetricsImpl(UserHealthProfileRepository profileRepository) {
    this.profileRepository = profileRepository;
  }

  @Override
  public Optional<BigDecimal> weightKg(UUID userId) {
    return profileRepository.findByUserId(userId).map(UserHealthProfile::getWeightKg);
  }

  @Override
  public Optional<BigDecimal> heightCm(UUID userId) {
    return profileRepository.findByUserId(userId).map(UserHealthProfile::getHeightCm);
  }
}
