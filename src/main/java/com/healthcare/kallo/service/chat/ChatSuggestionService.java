package com.healthcare.kallo.service.chat;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.config.ChatProperties;
import com.healthcare.kallo.dto.response.chat.ChatSuggestionResponse;
import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ChatSuggestionService {
  private static final List<ChatSuggestionResponse> DEFAULTS = List.of(
      new ChatSuggestionResponse("Hôm nay tôi còn bao nhiêu calo?", "Theo dõi mục tiêu hôm nay"),
      new ChatSuggestionResponse("Gợi ý bữa ăn phù hợp với mục tiêu của tôi", "Cá nhân hoá theo hồ sơ"),
      new ChatSuggestionResponse("Tôi nên uống bao nhiêu nước hôm nay?", "Theo dõi thói quen lành mạnh"));

  private final ChatContextProvider contextProvider;
  private final ChatProperties properties;

  public ChatSuggestionService(ChatContextProvider contextProvider, ChatProperties properties) {
    this.contextProvider = contextProvider;
    this.properties = properties;
  }

  public List<ChatSuggestionResponse> get(UUID userId) {
    if (!properties.enabled()) throw new BusinessException(ErrorCode.NOT_FOUND);
    ChatContextData context = contextProvider.get(userId);
    if (context == null || context.today().isEmpty()) return DEFAULTS;
    Object caloriesIn = context.today().get("consumedKcal");
    Object calorieGoal = context.today().get("calorieGoal");
    if (caloriesIn instanceof Number consumed && calorieGoal instanceof Number goal) {
      long remaining = goal.longValue() - consumed.longValue();
      String calorieReason = remaining >= 0
          ? "Còn khoảng " + remaining + " kcal" : "Đã vượt mục tiêu " + -remaining + " kcal";
      String mealPrompt = isEvening(context) ? "Tối nay tôi nên ăn gì?" : "Bữa tiếp theo tôi nên ăn gì?";
      return List.of(
          new ChatSuggestionResponse("Hôm nay tôi còn bao nhiêu calo?", calorieReason),
          new ChatSuggestionResponse(mealPrompt, "Phù hợp phần calo còn lại"),
          new ChatSuggestionResponse("Tôi đã ăn đủ protein chưa?", "Kiểm tra tiến độ dinh dưỡng"));
    }
    return DEFAULTS;
  }

  private boolean isEvening(ChatContextData context) {
    Object timezone = context.profile().get("timezone");
    try {
      ZoneId zone = ZoneId.of(timezone instanceof String value ? value : "Asia/Ho_Chi_Minh");
      LocalTime now = LocalTime.now(java.time.Clock.systemUTC().withZone(zone));
      return !now.isBefore(LocalTime.NOON);
    } catch (DateTimeException exception) {
      return false;
    }
  }
}
