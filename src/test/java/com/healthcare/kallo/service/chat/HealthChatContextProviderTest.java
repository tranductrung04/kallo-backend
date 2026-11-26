package com.healthcare.kallo.service.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.healthcare.kallo.model.auth.User;
import com.healthcare.kallo.model.profile.UserAllergy;
import com.healthcare.kallo.model.profile.UserAllergyId;
import com.healthcare.kallo.repository.auth.UserRepository;
import com.healthcare.kallo.repository.profile.UserAllergyRepository;
import com.healthcare.kallo.service.act.ProfileBodyMetrics;
import com.healthcare.kallo.service.game.DailyProgressProvider;
import com.healthcare.kallo.service.profile.port.IngredientLookup;
import com.healthcare.kallo.service.profile.port.IngredientSummary;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class HealthChatContextProviderTest {

  @Test
  @SuppressWarnings("unchecked")
  void resolvesAllergiesFromUserAllergiesTable() {
    UUID userId = UUID.randomUUID();
    UUID peanutId = UUID.randomUUID();

    User user = new User();
    user.setId(userId);
    user.setDisplayName("An");
    user.setTimezone("Asia/Ho_Chi_Minh");

    UserRepository users = mock(UserRepository.class);
    when(users.findById(userId)).thenReturn(Optional.of(user));

    UserAllergy allergy = new UserAllergy();
    allergy.setId(new UserAllergyId(userId, peanutId));
    UserAllergyRepository allergyRepo = mock(UserAllergyRepository.class);
    when(allergyRepo.findAllByIdUserId(userId)).thenReturn(List.of(allergy));

    IngredientLookup lookup = mock(IngredientLookup.class);
    when(lookup.findSummariesByIds(Set.of(peanutId)))
        .thenReturn(Map.of(peanutId, new IngredientSummary(peanutId, "Peanuts", true)));

    ObjectProvider<ProfileBodyMetrics> bodyMetrics = mock(ObjectProvider.class);
    ObjectProvider<DailyProgressProvider> dailyProgress = mock(ObjectProvider.class);
    ObjectProvider<UserAllergyRepository> allergyProvider = mock(ObjectProvider.class);
    when(allergyProvider.getIfAvailable()).thenReturn(allergyRepo);
    ObjectProvider<IngredientLookup> lookupProvider = mock(ObjectProvider.class);
    when(lookupProvider.getIfAvailable()).thenReturn(lookup);

    HealthChatContextProvider provider = new HealthChatContextProvider(
        users,
        bodyMetrics,
        dailyProgress,
        allergyProvider,
        lookupProvider,
        Clock.fixed(Instant.parse("2026-11-26T00:00:00Z"), ZoneOffset.UTC));

    ChatContextData data = provider.get(userId);

    assertThat(data.allergies()).containsExactly("Peanuts");
  }
}
