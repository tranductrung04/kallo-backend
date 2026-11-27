package com.healthcare.kallo.service.chat;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ChatPromptBuilder {

  private static final String BASE_SYSTEM_RULES = """
      Bạn là trợ lý dinh dưỡng và sức khoẻ cá nhân của ứng dụng Kallo.
      Nguyên tắc bắt buộc:
      1. Trả lời bằng tiếng Việt tự nhiên, ngắn gọn, rõ ràng, thân thiện.
      2. Chỉ tư vấn về dinh dưỡng, ăn uống, calo, macro, nước uống, cân nặng, vận động nhẹ và thói quen sống lành mạnh.
      3. Cá nhân hoá câu trả lời dựa đúng trên dữ liệu hồ sơ và nhật ký hôm nay của người dùng bên dưới; nếu dữ liệu nào chưa có thì nói rõ là chưa có dữ liệu thay vì tự bịa con số.
      4. TUYỆT ĐỐI KHÔNG gợi ý món ăn hoặc thực phẩm chứa thành phần nằm trong danh sách dị ứng của người dùng.
      5. Nếu người dùng có bệnh lý mạn tính, ưu tiên lựa chọn an toàn (ít đường tinh luyện với tiểu đường, ít natri/chiên rán với tăng huyết áp, hạn chế hải sản/thịt đỏ giàu purine với gút, theo dõi đạm/natri với bệnh thận mạn).
      6. Không chẩn đoán bệnh, không kê đơn thuốc và không thay thế tư vấn y khoa; với triệu chứng nghiêm trọng hoặc bệnh lý cần điều trị, khuyên người dùng gặp bác sĩ.
      7. Không đưa ra chế độ ăn cực đoan, nhịn ăn hoặc mức calo nguy hiểm.
      """;

  public String buildSystemPrompt(ChatAiRequest request) {
    StringBuilder sb = new StringBuilder(BASE_SYSTEM_RULES.trim());
    ChatAiRequest.ChatSystemContext ctx = request.systemContext();
    if (ctx == null) return sb.toString();

    if (!ctx.profile().isEmpty()) {
      sb.append("\n\n[Hồ sơ người dùng]");
      appendProfile(sb, ctx.profile());
    }

    if (!ctx.allergies().isEmpty()) {
      sb.append("\n\n[Dị ứng - BẮT BUỘC TRÁNH]: ")
          .append(String.join(", ", ctx.allergies()));
    }

    if (!ctx.chronicConditions().isEmpty()) {
      sb.append("\n\n[Bệnh lý mạn tính]: ")
          .append(ctx.chronicConditions().stream()
              .map(c -> c.code() != null ? c.name() + " (" + c.code() + ")" : c.name())
              .collect(Collectors.joining(", ")));
    }

    if (!ctx.today().isEmpty()) {
      sb.append("\n\n[Tổng kết dinh dưỡng & vận động hôm nay]");
      appendToday(sb, ctx.today());
    }

    if (!ctx.todayMeals().isEmpty()) {
      sb.append("\n\n[Các món đã ghi nhận hôm nay]");
      for (ChatContextData.TodayMealItem item : ctx.todayMeals()) {
        sb.append("\n- ").append(item.mealType()).append(": ").append(item.foodName());
        if (item.calories() != null) sb.append(" (").append(item.calories()).append(" kcal)");
      }
    }

    if (!ctx.recentWeight().isEmpty()) {
      sb.append("\n\n[Xu hướng cân nặng 30 ngày]");
      appendKeyVal(sb, "Cân nặng mới nhất (kg)", ctx.recentWeight().get("latestWeightKg"));
      appendKeyVal(sb, "Thay đổi 30 ngày (kg)", ctx.recentWeight().get("delta30dKg"));
      appendKeyVal(sb, "Xu hướng", ctx.recentWeight().get("trend"));
      appendKeyVal(sb, "Số lần ghi", ctx.recentWeight().get("logCount"));
    }

    if (request.conversationSummary() != null && !request.conversationSummary().isBlank()) {
      sb.append("\n\n[Tóm tắt hội thoại trước đó]\n").append(request.conversationSummary().trim());
    }

    return sb.toString();
  }

  public String buildSummaryPrompt(String existingSummary, List<ChatAiRequest.ChatHistoryMessage> olderMessages) {
    StringBuilder sb = new StringBuilder();
    sb.append("Hãy tóm tắt ngắn gọn (tối đa 120 từ, tiếng Việt, dạng gạch đầu dòng) các thông tin chính, ");
    sb.append("sở thích, món đã gợi ý và ngữ cảnh quan trọng từ đoạn hội thoại dưới đây để làm bộ nhớ cho lượt chat tiếp theo.\n");
    if (existingSummary != null && !existingSummary.isBlank()) {
      sb.append("\nTóm tắt trước đó:\n").append(existingSummary.trim()).append("\n");
    }
    sb.append("\nCác tin nhắn mới cần gộp vào tóm tắt:");
    for (ChatAiRequest.ChatHistoryMessage m : olderMessages) {
      sb.append("\n- ").append(m.role()).append(": ").append(m.content());
    }
    return sb.toString();
  }

  private void appendProfile(StringBuilder sb, Map<String, Object> profile) {
    appendKeyVal(sb, "Giới tính", profile.get("gender"));
    appendKeyVal(sb, "Tuổi", profile.get("age"));
    appendKeyVal(sb, "Chiều cao (cm)", profile.get("heightCm"));
    appendKeyVal(sb, "Cân nặng (kg)", profile.get("weightKg"));
    appendKeyVal(sb, "BMI", profile.get("bmi"));
    appendKeyVal(sb, "Mức độ vận động", profile.get("activityLevel"));
    appendKeyVal(sb, "Mục tiêu", profile.get("goalType"));
    appendKeyVal(sb, "Cân nặng mục tiêu (kg)", profile.get("targetWeightKg"));
    appendKeyVal(sb, "Tốc độ tuần (kg/tuần)", profile.get("weeklyRateKg"));
    appendKeyVal(sb, "BMR (kcal)", profile.get("bmr"));
    appendKeyVal(sb, "TDEE (kcal)", profile.get("tdee"));
    appendKeyVal(sb, "Mục tiêu calo/ngày (kcal)", profile.get("dailyCalorieGoal"));
    appendKeyVal(sb, "Mục tiêu Protein (g)", profile.get("proteinGoalG"));
    appendKeyVal(sb, "Mục tiêu Carbs (g)", profile.get("carbGoalG"));
    appendKeyVal(sb, "Mục tiêu Fat (g)", profile.get("fatGoalG"));
    appendKeyVal(sb, "Mục tiêu nước (lít)", profile.get("waterLiters"));
    appendKeyVal(sb, "Mục tiêu bước chân", profile.get("stepGoal"));
  }

  private void appendToday(StringBuilder sb, Map<String, Object> today) {
    appendKeyVal(sb, "Ngày", today.get("date"));
    appendKeyVal(sb, "Số bữa đã log", today.get("mealsLogged"));
    appendKeyVal(sb, "Calo đã nạp (kcal)", today.get("consumedKcal"));
    appendKeyVal(sb, "Mục tiêu calo (kcal)", today.get("calorieGoal"));
    appendKeyVal(sb, "Protein (g)", today.get("proteinG"));
    appendKeyVal(sb, "Carbs (g)", today.get("carbsG"));
    appendKeyVal(sb, "Fat (g)", today.get("fatG"));
    appendKeyVal(sb, "Đường (g)", today.get("sugarG"));
    appendKeyVal(sb, "Chất xơ (g)", today.get("fiberG"));
    appendKeyVal(sb, "Nước đã uống (ml)", today.get("waterMl"));
    appendKeyVal(sb, "Mục tiêu nước (ml)", today.get("waterGoalMl"));
    appendKeyVal(sb, "Bước chân", today.get("steps"));
    appendKeyVal(sb, "Calo tiêu hao vận động (kcal)", today.get("caloriesBurned"));
  }

  private void appendKeyVal(StringBuilder sb, String label, Object value) {
    if (value != null) {
      sb.append("\n- ").append(label).append(": ").append(value);
    }
  }
}
