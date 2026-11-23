package com.healthcare.kallo.service.report.engine;

import com.healthcare.kallo.dto.response.report.WeightForecastResponse;
import com.healthcare.kallo.model.profile.enums.GoalType;
import com.healthcare.kallo.model.report.DailySummary;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class WeightForecastService {

  public static final int MIN_REQUIRED_DAYS = 14;
  public static final double EMA_ALPHA = 0.3;
  public static final double KCAL_PER_KG_FAT = 7700.0;

  public WeightForecastResponse calculateForecast(
      int tdee,
      BigDecimal currentWeightKg,
      BigDecimal targetWeightKg,
      GoalType goalType,
      List<DailySummary> activeSummaries) {

    BigDecimal currentWeight = currentWeightKg != null ? currentWeightKg : BigDecimal.valueOf(65.0);
    BigDecimal targetWeight = targetWeightKg != null ? targetWeightKg : currentWeight;
    GoalType goal = goalType != null ? goalType : GoalType.MAINTAIN_WEIGHT;

    int dataCount = activeSummaries != null ? activeSummaries.size() : 0;
    if (dataCount < MIN_REQUIRED_DAYS) {
      int remainingNeeded = MIN_REQUIRED_DAYS - dataCount;
      return new WeightForecastResponse(
          "INSUFFICIENT_DATA",
          dataCount,
          MIN_REQUIRED_DAYS,
          currentWeight,
          targetWeight,
          tdee,
          null,
          null,
          "UNKNOWN",
          null,
          null,
          List.of(),
          "Bạn cần ghi nhật ký ăn uống và vận động thêm ít nhất " + remainingNeeded
              + " ngày nữa để thuật toán có đủ dữ liệu dự báo chính xác."
      );
    }

    // 1. Calculate daily net deficits and apply EMA (alpha = 0.3)
    List<Double> deficits = new ArrayList<>();
    for (DailySummary s : activeSummaries) {
      double burned = s.getCaloriesBurned();
      double consumed = s.getCaloriesConsumed();
      double netDeficit = consumed - tdee - burned;
      deficits.add(netDeficit);
    }

    double ema = deficits.get(0);
    for (int i = 1; i < deficits.size(); i++) {
      ema = EMA_ALPHA * deficits.get(i) + (1.0 - EMA_ALPHA) * ema;
    }

    int avgNetDeficitKcal = (int) Math.round(ema);
    double dailyWeightChangeKg = ema / KCAL_PER_KG_FAT;
    double weeklyWeightChangeKg = dailyWeightChangeKg * 7.0;

    // 2. 30-day projection
    List<WeightForecastResponse.ProjectionPoint> projections = new ArrayList<>();
    LocalDate today = LocalDate.now();
    for (int k = 1; k <= 30; k++) {
      double projectedWeight = currentWeight.doubleValue() + k * dailyWeightChangeKg;
      projections.add(new WeightForecastResponse.ProjectionPoint(
          k,
          today.plusDays(k),
          BigDecimal.valueOf(projectedWeight).setScale(2, RoundingMode.HALF_UP)
      ));
    }

    // 3. Assess Goal Trend & Estimated Goal Date
    String trend = "ON_TRACK";
    LocalDate estimatedGoalDate = null;
    Integer daysRemaining = null;

    if (goal == GoalType.LOSE_WEIGHT) {
      if (dailyWeightChangeKg >= -0.005) {
        trend = "OFF_TRACK";
      } else {
        trend = "ON_TRACK";
        double diff = targetWeight.doubleValue() - currentWeight.doubleValue();
        if (diff < 0) {
          long days = (long) Math.ceil(diff / dailyWeightChangeKg);
          if (days > 0 && days < 3650) {
            estimatedGoalDate = today.plusDays(days);
            daysRemaining = (int) days;
          }
        }
      }
    } else if (goal == GoalType.GAIN_WEIGHT) {
      if (dailyWeightChangeKg <= 0.005) {
        trend = "OFF_TRACK";
      } else {
        trend = "ON_TRACK";
        double diff = targetWeight.doubleValue() - currentWeight.doubleValue();
        if (diff > 0) {
          long days = (long) Math.ceil(diff / dailyWeightChangeKg);
          if (days > 0 && days < 3650) {
            estimatedGoalDate = today.plusDays(days);
            daysRemaining = (int) days;
          }
        }
      }
    } else {
      trend = Math.abs(dailyWeightChangeKg) <= 0.02 ? "MAINTAINING" : "OFF_TRACK";
    }

    String recommendation = buildRecommendation(goal, trend, avgNetDeficitKcal, weeklyWeightChangeKg);

    return new WeightForecastResponse(
        "SUFFICIENT_DATA",
        dataCount,
        MIN_REQUIRED_DAYS,
        currentWeight,
        targetWeight,
        tdee,
        avgNetDeficitKcal,
        BigDecimal.valueOf(weeklyWeightChangeKg).setScale(2, RoundingMode.HALF_UP),
        trend,
        estimatedGoalDate,
        daysRemaining,
        projections,
        recommendation
    );
  }

  private String buildRecommendation(GoalType goal, String trend, int deficit, double weeklyChange) {
    if ("ON_TRACK".equals(trend)) {
      if (goal == GoalType.LOSE_WEIGHT) {
        return "Bạn đang duy trì mức thâm hụt calo tốt (~" + Math.abs(deficit)
            + " kcal/ngày, tương đương " + String.format("%.2f", Math.abs(weeklyChange))
            + " kg/tuần). Hãy tiếp tục giữ vững tiến độ!";
      } else if (goal == GoalType.GAIN_WEIGHT) {
        return "Bạn đang duy trì mức thặng dư calo tích cực (~+" + deficit
            + " kcal/ngày). Cân nặng đang tiến triển đúng hướng mục tiêu!";
      }
    } else if ("OFF_TRACK".equals(trend)) {
      if (goal == GoalType.LOSE_WEIGHT) {
        return "Cân nặng đang có xu hướng chững lại hoặc tăng nhẹ do lượng calo nạp vào vượt mức thâm hụt khuyến nghị. Hãy cân nhắc giảm khẩu phần hoặc tăng cường vận động.";
      } else if (goal == GoalType.GAIN_WEIGHT) {
        return "Lượng calo nạp vào chưa đủ để tăng cân. Hãy bổ sung thêm các bữa phụ giàu dinh dưỡng.";
      }
    }
    return "Cân nặng của bạn đang duy trì ở mức ổn định.";
  }
}
