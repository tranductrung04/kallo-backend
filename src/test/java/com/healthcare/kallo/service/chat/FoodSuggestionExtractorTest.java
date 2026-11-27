package com.healthcare.kallo.service.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.healthcare.kallo.repository.food.FoodRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class FoodSuggestionExtractorTest {

  @Test
  @SuppressWarnings("unchecked")
  void extractsMentionedFoodsUsingVietnameseNormalizedWordBoundaries() {
    UUID userId = UUID.randomUUID();
    UUID phoId = UUID.randomUUID();
    UUID comTamId = UUID.randomUUID();
    UUID bunId = UUID.randomUUID();

    FoodRepository repo = mock(FoodRepository.class);
    when(repo.findActiveSuggestionCandidates(userId)).thenReturn(List.of(
        candidate(phoId, "Phở bò", "pho bo", 450),
        candidate(comTamId, "Cơm tấm sườn bì chả", "com tam suon bi cha", 620),
        candidate(bunId, "Bún riêu tôm", "bun rieu tom", 410)));

    ObjectProvider<FoodRepository> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(repo);
    FoodSuggestionExtractor extractor = new FoodSuggestionExtractor(provider);

    List<ChatAiReply.SuggestedFood> extracted = extractor.extract(
        userId, "Tối nay bạn có thể ăn Phở bò hoặc Bún riêu tôm nhé.");

    assertThat(extracted).extracting(ChatAiReply.SuggestedFood::name)
        .containsExactlyInAnyOrder("Phở bò", "Bún riêu tôm");
  }

  private FoodRepository.FoodSuggestionCandidate candidate(UUID id, String name, String unaccented, int kcal) {
    return new FoodRepository.FoodSuggestionCandidate() {
      @Override public UUID getId() { return id; }
      @Override public String getName() { return name; }
      @Override public String getNameUnaccented() { return unaccented; }
      @Override public Integer getCaloriesPerServing() { return kcal; }
    };
  }
}
