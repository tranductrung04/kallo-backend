package com.healthcare.kallo.service.chat;

import com.healthcare.kallo.repository.food.FoodRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FoodSuggestionExtractor {
  private static final int MAX_SUGGESTED_FOODS = 5;
  private static final int MIN_NAME_LENGTH = 3;
  private static final ConcurrentHashMap<String, Pattern> NAME_PATTERN_CACHE = new ConcurrentHashMap<>();

  private final ObjectProvider<FoodRepository> foodRepository;

  @Autowired
  public FoodSuggestionExtractor(ObjectProvider<FoodRepository> foodRepository) {
    this.foodRepository = foodRepository;
  }

  public List<ChatAiReply.SuggestedFood> extract(UUID userId, String content) {
    if (userId == null || content == null || content.isBlank()) {
      return List.of();
    }
    FoodRepository repo = foodRepository == null ? null : foodRepository.getIfAvailable();
    if (repo == null) {
      return List.of();
    }
    List<FoodRepository.FoodSuggestionCandidate> candidates = repo.findActiveSuggestionCandidates(userId);
    if (candidates == null || candidates.isEmpty()) {
      return List.of();
    }

    String normalizedContent = ChatSafetyService.normalizeVietnamese(content);
    List<FoodRepository.FoodSuggestionCandidate> sorted = candidates.stream()
        .filter(c -> c.getName() != null && !c.getName().isBlank())
        .sorted(Comparator.comparingInt((FoodRepository.FoodSuggestionCandidate c) -> c.getName().length()).reversed())
        .toList();

    List<ChatAiReply.SuggestedFood> matched = new ArrayList<>();
    Set<String> seenNames = new LinkedHashSet<>();
    for (FoodRepository.FoodSuggestionCandidate candidate : sorted) {
      String normName = candidate.getNameUnaccented() != null && !candidate.getNameUnaccented().isBlank()
          ? candidate.getNameUnaccented().trim()
          : ChatSafetyService.normalizeVietnamese(candidate.getName());
      if (normName.length() < MIN_NAME_LENGTH || !seenNames.add(normName)) {
        continue;
      }
      if (compiledPhrasePattern(normName).matcher(normalizedContent).find()) {
        matched.add(new ChatAiReply.SuggestedFood(
            candidate.getId(),
            candidate.getName(),
            candidate.getCaloriesPerServing()));
        if (matched.size() >= MAX_SUGGESTED_FOODS) {
          break;
        }
      }
    }
    return matched;
  }

  private static Pattern compiledPhrasePattern(String normName) {
    return NAME_PATTERN_CACHE.computeIfAbsent(normName, key -> {
      String[] words = key.split("\\s+");
      StringBuilder expr = new StringBuilder();
      for (int i = 0; i < words.length; i++) {
        if (i > 0) expr.append("\\s+");
        expr.append(Pattern.quote(words[i]));
      }
      return Pattern.compile("(?<![\\p{L}\\p{N}])" + expr + "(?![\\p{L}\\p{N}])", Pattern.UNICODE_CASE);
    });
  }
}
