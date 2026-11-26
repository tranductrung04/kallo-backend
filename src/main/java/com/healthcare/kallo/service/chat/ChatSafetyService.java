package com.healthcare.kallo.service.chat;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class ChatSafetyService {
  private static final String ALLERGEN_REPLACEMENT =
      "Mình không thể giữ gợi ý này vì có thể chứa thành phần bạn dị ứng. "
          + "Hãy chọn món có thành phần rõ ràng và kiểm tra nhãn trước khi dùng.";

  public static final String MEDICAL_DISCLAIMER =
      "\n\nLưu ý: Thông tin trên chỉ mang tính tham khảo về dinh dưỡng, không thay thế chẩn đoán hay tư vấn trực tiếp của bác sĩ.";

  private static final String SELF_HARM_SAFE_REPLY =
      "Mình rất quan tâm đến sự an toàn của bạn. Kallo không hỗ trợ các chế độ nhịn ăn cực đoan "
          + "hoặc giảm cân cấp tốc gây hại cho sức khoẻ.\n\n"
          + "Nếu bạn đang cảm thấy quá áp lực về cân nặng, ăn uống hoặc tâm lý, hãy chia sẻ với người thân, "
          + "bác sĩ hoặc chuyên gia tâm lý/dinh dưỡng để được hỗ trợ an toàn nhé.";

  private static final String OUT_OF_SCOPE_SAFE_REPLY =
      "Mình là trợ lý dinh dưỡng và theo dõi sức khoẻ của Kallo, nên không thể chẩn đoán bệnh, "
          + "kê đơn thuốc hoặc giải đáp các chủ đề ngoài phạm vi sức khoẻ – dinh dưỡng.\n\n"
          + "Bạn có thể hỏi mình về lượng calo hôm nay, gợi ý bữa ăn hợp mục tiêu, tra cứu dinh dưỡng món ăn "
          + "hoặc theo dõi nước uống và cân nặng nhé.";

  private static final Pattern KCAL_MENTION = Pattern.compile("(\\d{2,4})\\s*(?:kcal|calo|calories)");

  private static final Pattern SELF_HARM_ED_PATTERN = Pattern.compile(
      "(?<![\\p{L}\\p{N}])(?:nhin an|nhin doi|khong an gi|moc hong|tu lam hai|tu tu|ep can cap toc|bo an han)(?![\\p{L}\\p{N}])");

  private static final Pattern MEDICAL_PRESCRIPTION_PATTERN = Pattern.compile(
      "(?<![\\p{L}\\p{N}])(?:chan doan|ke don|don thuoc|uong thuoc gi|lieu thuoc|kham benh|xet nghiem|dau nguc|kho tho|sot cao)(?![\\p{L}\\p{N}])");

  private static final Pattern OFF_TOPIC_PATTERN = Pattern.compile(
      "(?<![\\p{L}\\p{N}])(?:lap trinh|viet code|chung khoan|gia vang|thoi tiet|bong da|xo so|giai toan)(?![\\p{L}\\p{N}])");

  private static final Pattern NUTRITION_ANCHOR_PATTERN = Pattern.compile(
      "(?<![\\p{L}\\p{N}])(?:an|uong|mon|thuc don|bua|calo|kcal|protein|carb|fat|duong|chat xo|dinh duong|can nang|bmi|tdee|nuoc|buoc chan|van dong)(?![\\p{L}\\p{N}])");

  private final ChatMetrics metrics;

  public ChatSafetyService(ChatMetrics metrics) {
    this.metrics = metrics;
  }

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
    return sanitizePromptInjection(normalized);
  }

  public String sanitizePromptInjection(String content) {
    if (content == null) return "";
    String cleaned = PROMPT_INJECTION_PATTERN.matcher(content).replaceAll(" ").trim();
    return cleaned.isBlank() ? content : cleaned;
  }

  public InputPolicyDecision checkInputPolicy(String content) {
    String norm = normalizeVietnamese(content);
    if (SELF_HARM_ED_PATTERN.matcher(norm).find() || isExtremeLowCalorieRequest(norm)) {
      metrics.recordSafetyBlocked("self_harm_or_ed");
      return new InputPolicyDecision(true, SELF_HARM_SAFE_REPLY, "self_harm_or_ed");
    }
    boolean hasNutritionAnchor = NUTRITION_ANCHOR_PATTERN.matcher(norm).find();
    if (MEDICAL_PRESCRIPTION_PATTERN.matcher(norm).find()
        || (OFF_TOPIC_PATTERN.matcher(norm).find() && !hasNutritionAnchor)) {
      metrics.recordSafetyBlocked("out_of_scope");
      return new InputPolicyDecision(true, OUT_OF_SCOPE_SAFE_REPLY, "out_of_scope");
    }
    return new InputPolicyDecision(false, null, null);
  }

  private boolean isExtremeLowCalorieRequest(String norm) {
    if (!norm.contains("an duoi") && !norm.contains("chi an") && !norm.contains("giam xuong") && !norm.contains("che do")) {
      return false;
    }
    Matcher m = KCAL_MENTION.matcher(norm);
    while (m.find()) {
      int val = Integer.parseInt(m.group(1));
      if (val > 0 && val < 800) return true;
    }
    return false;
  }

  public String validateOutput(String content) {
    return validateOutputWithGuardrails(content, List.of()).content();
  }

  public OutputValidation validateOutputWithGuardrails(String content, List<String> allergies) {
    return validateOutputWithGuardrails(content, allergies, List.of(), false);
  }

  public OutputValidation validateOutputWithGuardrails(
      String content, List<String> allergies, List<ChatAiReply.SuggestedFood> suggestedFoods) {
    return validateOutputWithGuardrails(content, allergies, suggestedFoods, false);
  }

  public OutputValidation validateOutputWithGuardrails(
      String content,
      List<String> allergies,
      List<ChatAiReply.SuggestedFood> suggestedFoods,
      boolean medicalContext) {
    if (content == null || content.isBlank()) {
      throw new BusinessException(ErrorCode.CHAT_AI_INVALID_RESPONSE);
    }
    String normalized = content.trim();
    List<ChatAiReply.SuggestedFood> safeFoods = suggestedFoods == null
        ? List.of()
        : suggestedFoods.stream()
            .filter(food -> food != null && !containsAnyAllergen(food.name(), allergies))
            .toList();
    boolean contentUnsafe = containsAnyAllergen(normalized, allergies);
    boolean foodsUnsafe = safeFoods.size() != (suggestedFoods == null ? 0 : suggestedFoods.size());
    if (contentUnsafe) {
      return new OutputValidation(ALLERGEN_REPLACEMENT, List.of(), true, false);
    }
    boolean disclaimerAdded = false;
    if ((medicalContext || containsMedicalKeywords(normalized)) && !alreadyHasDisclaimer(normalized)) {
      normalized = normalized + MEDICAL_DISCLAIMER;
      disclaimerAdded = true;
    }
    return new OutputValidation(normalized, safeFoods, foodsUnsafe, disclaimerAdded);
  }

  public boolean containsAnyAllergen(String content, List<String> allergies) {
    if (content == null || content.isBlank() || allergies == null || allergies.isEmpty()) {
      return false;
    }
    return allergies.stream().anyMatch(allergy -> containsIngredient(content, allergy));
  }

  public String validateAndNormalizeTitle(String title) {
    String normalized = title == null
        ? ""
        : title.trim().replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "").replaceAll("[\\r\\n\\t]+", " ");
    if (normalized.isBlank()) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
    return normalized.codePoints().limit(120)
        .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
        .toString();
  }

  public static String normalizeVietnamese(String value) {
    if (value == null) return "";
    return Normalizer.normalize(value, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .replace('đ', 'd')
        .replace('Đ', 'D')
        .toLowerCase(Locale.ROOT)
        .trim();
  }

  private static final Pattern PROMPT_INJECTION_PATTERN = Pattern.compile(
      "(?i)(?:ignore\\s+(?:all\\s+)?previous\\s+instructions|<\\|im_start\\|>|<\\|im_end\\|>|\\[INST\\]|^\\s*(?:system|assistant)\\s*:)");

  private static final Pattern MEDICAL_CONTENT_PATTERN = Pattern.compile(
      "(?<![\\p{L}\\p{N}])(?:tieu duong|dai thao duong|tang huyet ap|benh gut|benh than|thuoc|insulin|mang thai|thai ky|cho con bu|bac si|chan doan)(?![\\p{L}\\p{N}])");

  private static final Set<String> GENERIC_ALLERGY_TOKENS = Set.of(
      "cac loai", "san pham tu", "va", "hat cay", "giap xac", "than mem", "food", "allergy");

  private static final Map<String, Set<String>> ALLERGEN_GROUPS = Map.ofEntries(
      Map.entry("PEANUT", Set.of("lac", "dau phong", "dau phung", "peanut", "groundnut")),
      Map.entry("lac", Set.of("lac", "dau phong", "dau phung", "peanut", "groundnut")),
      Map.entry("dau phong", Set.of("lac", "dau phong", "dau phung", "peanut", "groundnut")),
      Map.entry("dau phung", Set.of("lac", "dau phong", "dau phung", "peanut", "groundnut")),
      Map.entry("peanut", Set.of("lac", "dau phong", "dau phung", "peanut", "groundnut")),

      Map.entry("DAIRY", Set.of("sua", "pho mai", "kem sua", "yogurt", "sua chua", "whey", "casein", "milk", "dairy", "cheese", "butter")),
      Map.entry("sua", Set.of("sua", "pho mai", "kem sua", "yogurt", "sua chua", "milk", "dairy", "cheese")),
      Map.entry("milk", Set.of("sua", "pho mai", "yogurt", "sua chua", "milk", "dairy", "cheese")),
      Map.entry("dairy", Set.of("sua", "pho mai", "yogurt", "sua chua", "milk", "dairy", "cheese")),

      Map.entry("EGG", Set.of("trung", "egg", "mayonnaise")),
      Map.entry("trung", Set.of("trung", "egg", "mayonnaise")),
      Map.entry("egg", Set.of("trung", "egg", "mayonnaise")),

      Map.entry("TREE_NUTS", Set.of("hanh nhan", "oc cho", "hat dieu", "hat de", "macca", "ho dao", "almond", "walnut", "cashew", "pistachio", "hazelnut", "tree nut")),
      Map.entry("hanh nhan", Set.of("hanh nhan", "oc cho", "hat dieu", "hat de", "macca", "almond", "walnut", "cashew")),
      Map.entry("oc cho", Set.of("hanh nhan", "oc cho", "hat dieu", "hat de", "macca", "almond", "walnut", "cashew")),
      Map.entry("hat dieu", Set.of("hanh nhan", "oc cho", "hat dieu", "hat de", "macca", "almond", "walnut", "cashew")),

      Map.entry("SOY", Set.of("dau nanh", "dau hu", "tau hu", "xi dau", "nuoc tuong", "chao", "soy", "soya", "tofu", "edamame")),
      Map.entry("dau nanh", Set.of("dau nanh", "dau hu", "tau hu", "xi dau", "nuoc tuong", "soy", "soya", "tofu")),
      Map.entry("soy", Set.of("dau nanh", "dau hu", "tau hu", "xi dau", "nuoc tuong", "soy", "soya", "tofu")),

      Map.entry("GLUTEN", Set.of("gluten", "lua mi", "bot mi", "banh mi", "mi y", "mi ong", "lua mach", "wheat", "barley", "rye", "pasta")),
      Map.entry("gluten", Set.of("gluten", "lua mi", "bot mi", "banh mi", "mi y", "mi ong", "wheat", "pasta")),
      Map.entry("lua mi", Set.of("gluten", "lua mi", "bot mi", "banh mi", "wheat")),

      Map.entry("FISH", Set.of("ca", "nuoc mam", "ca hoi", "ca thu", "ca ngu", "ca loc", "ca basa", "fish", "salmon", "tuna")),
      Map.entry("ca", Set.of("ca", "ca hoi", "ca thu", "ca ngu", "ca loc", "ca basa", "fish", "salmon", "tuna")),
      Map.entry("fish", Set.of("ca", "ca hoi", "ca thu", "ca ngu", "fish", "salmon", "tuna")),

      Map.entry("CRUSTACEANS", Set.of("tom", "tep", "cua", "ghe", "tom hum", "shrimp", "prawn", "crab", "lobster")),
      Map.entry("tom", Set.of("tom", "tep", "shrimp", "prawn")),
      Map.entry("tep", Set.of("tom", "tep", "shrimp", "prawn")),
      Map.entry("cua", Set.of("cua", "ghe", "crab")),
      Map.entry("ghe", Set.of("cua", "ghe", "crab")),
      Map.entry("hai san", Set.of("hai san", "tom", "tep", "cua", "ghe", "muc", "oc", "ngheu", "so", "hau", "oyster", "shrimp", "prawn", "crab", "seafood")),

      Map.entry("SESAME", Set.of("me", "vung", "dau me", "sesame", "tahini")),
      Map.entry("me", Set.of("me", "vung", "dau me", "sesame")),
      Map.entry("vung", Set.of("me", "vung", "dau me", "sesame")),
      Map.entry("sesame", Set.of("me", "vung", "dau me", "sesame")),

      Map.entry("MOLLUSCS", Set.of("oc", "muc", "ngheu", "so", "hau", "bach tuoc", "ngao", "oyster", "squid", "octopus", "clam", "mussel", "scallop", "snail")),
      Map.entry("oc", Set.of("oc", "muc", "ngheu", "so", "hau", "bach tuoc", "ngao", "squid", "clam", "snail")),
      Map.entry("muc", Set.of("oc", "muc", "ngheu", "so", "hau", "bach tuoc", "squid", "octopus")));

  private static final Pattern NEGATED_OR_AVOIDANCE_CLAUSE = Pattern.compile(
      "(?<![\\p{L}\\p{N}])(?:khong|without|no|free of|tranh|kieng|khong nen(?: an| dung)?|di ung(?: voi)?|loai bo|bo qua)(?:\\s+[\\p{L}\\p{N}]+){0,4}\\s*$");

  private static final Map<String, Set<String>> SHORT_TERM_VARIANTS = Map.of(
      "ca", Set.of("cá"),
      "me", Set.of("mè"),
      "trung", Set.of("trứng"),
      "lac", Set.of("lạc"),
      "cua", Set.of("cua"),
      "oc", Set.of("ốc"),
      "so", Set.of("sò"),
      "sua", Set.of("sữa"));

  private boolean containsIngredient(String content, String allergy) {
    Set<String> ingredients = resolveAllergenTerms(allergy);
    if (ingredients.isEmpty()) return false;
    String normalizedContent = normalizeVietnamese(content);
    String originalContent = content == null ? "" : content.toLowerCase(Locale.ROOT);
    return ingredients.stream().anyMatch(ingredient -> {
      Set<String> variants = SHORT_TERM_VARIANTS.get(ingredient);
      return variants == null
          ? containsTerm(normalizedContent, ingredient)
          : variants.stream().anyMatch(term -> containsTerm(originalContent, term));
    });
  }

  private Set<String> resolveAllergenTerms(String allergy) {
    if (allergy == null || allergy.isBlank()) return Set.of();
    String trimmed = allergy.trim();
    String upperCode = trimmed.toUpperCase(Locale.ROOT);
    if (ALLERGEN_GROUPS.containsKey(upperCode)) {
      return ALLERGEN_GROUPS.get(upperCode);
    }
    String normalized = normalizeVietnamese(trimmed);
    if (normalized.isBlank()) return Set.of();
    if (ALLERGEN_GROUPS.containsKey(normalized)) {
      return ALLERGEN_GROUPS.get(normalized);
    }

    Set<String> resolved = new LinkedHashSet<>();
    String[] parts = normalized.split("[,/();:\\-]+|\\s+va\\s+");
    for (String part : parts) {
      String token = part.replace("cac loai", "").replace("san pham tu", "").trim();
      if (token.isBlank() || GENERIC_ALLERGY_TOKENS.contains(token)) continue;
      if (ALLERGEN_GROUPS.containsKey(token)) {
        resolved.addAll(ALLERGEN_GROUPS.get(token));
      } else {
        for (String sub : token.split("\\s+")) {
          if (ALLERGEN_GROUPS.containsKey(sub)) {
            resolved.addAll(ALLERGEN_GROUPS.get(sub));
          }
        }
        if (token.length() >= 3) {
          resolved.add(token);
        }
      }
    }
    if (resolved.isEmpty()) {
      resolved.add(normalized);
    }
    return resolved;
  }

  private boolean containsTerm(String content, String term) {
    String expression = Pattern.quote(term).replace("\\Q \\E", "\\\\s+");
    Matcher matcher = Pattern.compile(
        "(?<![\\p{L}\\p{N}])" + expression + "(?![\\p{L}\\p{N}])",
        Pattern.UNICODE_CASE).matcher(content);
    while (matcher.find()) {
      String before = content.substring(0, matcher.start());
      int clauseStart = lastClauseBreak(before);
      String clausePrefix = normalizeVietnamese(before.substring(clauseStart));
      if (!NEGATED_OR_AVOIDANCE_CLAUSE.matcher(clausePrefix).find()) {
        return true;
      }
    }
    return false;
  }

  private int lastClauseBreak(String text) {
    int last = -1;
    for (char ch : new char[] {',', '.', ';', '!', '?', '\n', ':'}) {
      int idx = text.lastIndexOf(ch);
      if (idx > last) last = idx;
    }
    return last + 1;
  }

  private boolean containsMedicalKeywords(String text) {
    return MEDICAL_CONTENT_PATTERN.matcher(normalizeVietnamese(text)).find();
  }

  private boolean alreadyHasDisclaimer(String text) {
    String normalized = normalizeVietnamese(text);
    return normalized.contains("khong thay the tu van bac si")
        || normalized.contains("tham khao y kien bac si")
        || normalized.contains("chi mang tinh tham khao");
  }

  public record InputPolicyDecision(boolean blocked, String safeResponse, String reason) {}

  public record OutputValidation(
      String content,
      List<ChatAiReply.SuggestedFood> suggestedFoods,
      boolean replaced,
      boolean disclaimerAdded) {
    public OutputValidation(String content, List<ChatAiReply.SuggestedFood> suggestedFoods, boolean replaced) {
      this(content, suggestedFoods, replaced, false);
    }
  }
}
