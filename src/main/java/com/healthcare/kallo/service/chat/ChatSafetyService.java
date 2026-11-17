package com.healthcare.kallo.service.chat;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class ChatSafetyService {
  private final ChatMetrics metrics;

  public ChatSafetyService(ChatMetrics metrics) {
    this.metrics = metrics;
  }
  private static final String ALLERGEN_REPLACEMENT =
      "Mình không thể giữ gợi ý này vì có thể chứa thành phần bạn dị ứng. "
          + "Hãy chọn món có thành phần rõ ràng và kiểm tra nhãn trước khi dùng.";

  public String validateAndNormalizeInput(String content, int maxLength) {
    String normalized = content == null ? "" : content.trim().replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "");
    if (normalized.isBlank()) {
      metrics.recordSafetyBlocked("blank_input");
      throw new BusinessException(ErrorCode.VALIDATION_ERROR);
    }
    if (normalized.codePointCount(0, normalized.length()) > maxLength) {
      metrics.recordSafetyBlocked("payload_too_large");
      throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE);
    }
    return normalized;
  }

  public String validateOutput(String content) {
    return validateOutputWithGuardrails(content, List.of()).content();
  }

  public OutputValidation validateOutputWithGuardrails(String content, List<String> allergies) {
    return validateOutputWithGuardrails(content, allergies, List.of());
  }

  public OutputValidation validateOutputWithGuardrails(String content, List<String> allergies,
      List<ChatAiReply.SuggestedFood> suggestedFoods) {
    if (content == null || content.isBlank()) throw new BusinessException(ErrorCode.CHAT_AI_INVALID_RESPONSE);
    String normalized = content.trim();
    List<ChatAiReply.SuggestedFood> safeFoods = suggestedFoods == null ? List.of()
        : suggestedFoods.stream().filter(food -> food != null
            && (allergies == null || allergies.stream().noneMatch(allergy -> containsIngredient(food.name(), allergy)))).toList();
    boolean contentUnsafe = allergies != null && allergies.stream().anyMatch(allergy -> containsIngredient(normalized, allergy));
    boolean foodsUnsafe = safeFoods.size() != (suggestedFoods == null ? 0 : suggestedFoods.size());
    if (contentUnsafe) return new OutputValidation(ALLERGEN_REPLACEMENT, List.of(), true);
    return new OutputValidation(normalized, safeFoods, foodsUnsafe);
  }

  public String validateAndNormalizeTitle(String title) {
    String normalized = title == null ? "" : title.trim().replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "")
        .replaceAll("[\\r\\n\\t]+", " ");
    if (normalized.isBlank()) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
    return normalized.codePoints().limit(120).collect(StringBuilder::new,
        StringBuilder::appendCodePoint, StringBuilder::append).toString();
  }

  private static final Map<String, Set<String>> ALLERGEN_GROUPS = Map.of(
      "tom", Set.of("tom", "tep", "shrimp", "prawn"),
      "tep", Set.of("tom", "tep", "shrimp", "prawn"),
      "hai san", Set.of("hai san", "tom", "tep", "cua", "ghe", "muc", "oyster", "shrimp", "prawn", "seafood"),
      "lac", Set.of("lac", "dau phong", "peanut", "groundnut"),
      "dau phong", Set.of("lac", "dau phong", "peanut", "groundnut"),
      "sua", Set.of("sua", "milk", "dairy"));

  private boolean containsIngredient(String content, String allergy) {
    String normalizedAllergy = normalize(allergy);
    if (normalizedAllergy.isBlank()) return false;
    Set<String> ingredients = ALLERGEN_GROUPS.getOrDefault(normalizedAllergy, Set.of(normalizedAllergy));
    String normalizedContent = normalize(content);
    return ingredients.stream().anyMatch(ingredient -> containsTerm(normalizedContent, ingredient));
  }

  private boolean containsTerm(String content, String term) {
    String expression = Pattern.quote(term).replace("\\Q \\E", "\\\\s+");
    Matcher matcher = Pattern.compile("(?<![\\p{L}\\p{N}])" + expression
        + "(?![\\p{L}\\p{N}])").matcher(content);
    return matcher.find();
  }

  private String normalize(String value) {
    if (value == null) return "";
    return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
        .replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT).trim();
  }

  public record OutputValidation(String content, List<ChatAiReply.SuggestedFood> suggestedFoods, boolean replaced) {}
}
