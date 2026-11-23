package com.healthcare.kallo.service.report.impl;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.dto.response.report.*;
import com.healthcare.kallo.model.profile.UserHealthProfile;
import com.healthcare.kallo.model.report.DailySummary;
import com.healthcare.kallo.repository.profile.UserHealthProfileRepository;
import com.healthcare.kallo.repository.report.DailySummaryRepository;
import com.healthcare.kallo.service.report.ReportService;
import com.healthcare.kallo.service.report.engine.WeightForecastService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReportServiceImpl implements ReportService {

  private final DailySummaryRepository dailySummaryRepository;
  private final UserHealthProfileRepository userHealthProfileRepository;
  private final WeightForecastService weightForecastService;

  public ReportServiceImpl(
      DailySummaryRepository dailySummaryRepository,
      UserHealthProfileRepository userHealthProfileRepository,
      WeightForecastService weightForecastService) {
    this.dailySummaryRepository = dailySummaryRepository;
    this.userHealthProfileRepository = userHealthProfileRepository;
    this.weightForecastService = weightForecastService;
  }

  @Override
  public DashboardReportResponse getDashboard(UUID userId, LocalDate date) {
    LocalDate targetDate = date != null ? date : LocalDate.now();

    UserHealthProfile profile = userHealthProfileRepository.findByUserId(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.PROFILE_NOT_FOUND, "Profile not found"));

    Optional<DailySummary> summaryOpt = dailySummaryRepository.findByUserIdAndSummaryDate(userId, targetDate);

    int consumed = summaryOpt.map(DailySummary::getCaloriesConsumed).orElse(0);
    int target = summaryOpt.map(DailySummary::getTargetCalories).orElse(profile.getDailyCalorieGoal());
    int burned = summaryOpt.map(DailySummary::getCaloriesBurned).orElse(0);
    int net = consumed - burned;
    int remaining = target - consumed;
    double pct = target > 0 ? (consumed * 100.0) / target : 0.0;

    String caloStatus;
    if (consumed < target - 100) {
      caloStatus = "DEFICIT";
    } else if (consumed > target + 100) {
      caloStatus = "SURPLUS";
    } else {
      caloStatus = "BALANCED";
    }

    DashboardReportResponse.CalorieStats caloStats = new DashboardReportResponse.CalorieStats(
        consumed, target, burned, net, remaining, caloStatus, Math.round(pct * 10.0) / 10.0
    );

    BigDecimal pConsumed = summaryOpt.map(DailySummary::getProteinG).orElse(BigDecimal.ZERO);
    BigDecimal cConsumed = summaryOpt.map(DailySummary::getCarbsG).orElse(BigDecimal.ZERO);
    BigDecimal fConsumed = summaryOpt.map(DailySummary::getFatG).orElse(BigDecimal.ZERO);
    BigDecimal sConsumed = summaryOpt.map(DailySummary::getSugarG).orElse(BigDecimal.ZERO);
    BigDecimal fibConsumed = summaryOpt.map(DailySummary::getFiberG).orElse(BigDecimal.ZERO);

    DashboardReportResponse.MacroStats macroStats = new DashboardReportResponse.MacroStats(
        new DashboardReportResponse.NutrientProgress(
            pConsumed, profile.getProteinGoalG(),
            calcPct(pConsumed, profile.getProteinGoalG())
        ),
        new DashboardReportResponse.NutrientProgress(
            cConsumed, profile.getCarbsGoalG(),
            calcPct(cConsumed, profile.getCarbsGoalG())
        ),
        new DashboardReportResponse.NutrientProgress(
            fConsumed, profile.getFatGoalG(),
            calcPct(fConsumed, profile.getFatGoalG())
        ),
        new DashboardReportResponse.SugarProgress(
            sConsumed, summaryOpt.map(DailySummary::isSugarExceeded).orElse(false)
        ),
        new DashboardReportResponse.FiberProgress(fibConsumed)
    );

    int waterConsumed = summaryOpt.map(DailySummary::getWaterMl).orElse(0);
    int waterTarget = summaryOpt.map(DailySummary::getTargetWaterMl).orElse(profile.getWaterGoalMl());
    double waterPct = waterTarget > 0 ? (waterConsumed * 100.0) / waterTarget : 0.0;

    DashboardReportResponse.WaterStats waterStats = new DashboardReportResponse.WaterStats(
        waterConsumed, waterTarget, Math.round(waterPct * 10.0) / 10.0
    );

    BigDecimal currentWeight = summaryOpt.flatMap(s -> Optional.ofNullable(s.getWeightKg()))
        .orElse(profile.getWeightKg());

    DashboardReportResponse.ActivityStats activityStats = new DashboardReportResponse.ActivityStats(
        summaryOpt.map(DailySummary::getSteps).orElse(0),
        burned,
        currentWeight
    );

    DashboardReportResponse.GoalStats goalStats = new DashboardReportResponse.GoalStats(
        summaryOpt.map(DailySummary::isGoalAchieved).orElse(false),
        summaryOpt.map(DailySummary::isSugarExceeded).orElse(false),
        summaryOpt.map(DailySummary::isProteinExceeded).orElse(false),
        summaryOpt.map(DailySummary::getMealsLoggedCount).orElse(0)
    );

    return new DashboardReportResponse(targetDate, caloStats, macroStats, waterStats, activityStats, goalStats);
  }

  @Override
  public CaloriesReportResponse getCaloriesReport(UUID userId, String period, LocalDate endDate) {
    int days = resolveDays(period);
    LocalDate end = endDate != null ? endDate : LocalDate.now();
    LocalDate start = end.minusDays(days - 1);

    UserHealthProfile profile = userHealthProfileRepository.findByUserId(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.PROFILE_NOT_FOUND, "Profile not found"));

    List<DailySummary> summaries = dailySummaryRepository.findByUserIdAndSummaryDateBetweenOrderBySummaryDateAsc(userId, start, end);
    Map<LocalDate, DailySummary> summaryMap = summaries.stream()
        .collect(Collectors.toMap(DailySummary::getSummaryDate, Function.identity(), (a, b) -> a));

    List<CaloriesReportResponse.Item> timeSeries = new ArrayList<>();
    int totalConsumed = 0;
    int totalBurned = 0;
    int trackedDays = 0;
    int metGoalCount = 0;

    for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
      DailySummary s = summaryMap.get(d);
      if (s != null && s.getCaloriesConsumed() > 0) {
        int consumed = s.getCaloriesConsumed();
        int burned = s.getCaloriesBurned();
        int target = s.getTargetCalories();
        boolean goalAchieved = s.isGoalAchieved();

        timeSeries.add(new CaloriesReportResponse.Item(d, consumed, burned, target, consumed - burned, goalAchieved, true));
        totalConsumed += consumed;
        totalBurned += burned;
        trackedDays++;
        if (goalAchieved) metGoalCount++;
      } else {
        timeSeries.add(new CaloriesReportResponse.Item(d, 0, 0, profile.getDailyCalorieGoal(), 0, false, false));
      }
    }

    int avgConsumed = trackedDays > 0 ? totalConsumed / trackedDays : 0;
    int avgBurned = trackedDays > 0 ? totalBurned / trackedDays : 0;

    return new CaloriesReportResponse(
        period != null ? period.toUpperCase() : "WEEK",
        start, end, avgConsumed, avgBurned, trackedDays, metGoalCount, timeSeries
    );
  }

  @Override
  public MacrosReportResponse getMacrosReport(UUID userId, String period, LocalDate endDate) {
    int days = resolveDays(period);
    LocalDate end = endDate != null ? endDate : LocalDate.now();
    LocalDate start = end.minusDays(days - 1);

    List<DailySummary> summaries = dailySummaryRepository.findByUserIdAndSummaryDateBetweenOrderBySummaryDateAsc(userId, start, end);
    Map<LocalDate, DailySummary> summaryMap = summaries.stream()
        .collect(Collectors.toMap(DailySummary::getSummaryDate, Function.identity(), (a, b) -> a));

    List<MacrosReportResponse.Item> timeSeries = new ArrayList<>();
    BigDecimal totalP = BigDecimal.ZERO;
    BigDecimal totalC = BigDecimal.ZERO;
    BigDecimal totalF = BigDecimal.ZERO;
    int trackedDays = 0;

    for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
      DailySummary s = summaryMap.get(d);
      if (s != null && s.getCaloriesConsumed() > 0) {
        timeSeries.add(new MacrosReportResponse.Item(d, s.getProteinG(), s.getCarbsG(), s.getFatG(), true));
        totalP = totalP.add(s.getProteinG());
        totalC = totalC.add(s.getCarbsG());
        totalF = totalF.add(s.getFatG());
        trackedDays++;
      } else {
        timeSeries.add(new MacrosReportResponse.Item(d, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false));
      }
    }

    BigDecimal avgP = trackedDays > 0 ? totalP.divide(BigDecimal.valueOf(trackedDays), 1, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    BigDecimal avgC = trackedDays > 0 ? totalC.divide(BigDecimal.valueOf(trackedDays), 1, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    BigDecimal avgF = trackedDays > 0 ? totalF.divide(BigDecimal.valueOf(trackedDays), 1, RoundingMode.HALF_UP) : BigDecimal.ZERO;

    double caloFromP = avgP.doubleValue() * 4.0;
    double caloFromC = avgC.doubleValue() * 4.0;
    double caloFromF = avgF.doubleValue() * 9.0;
    double totalMacroCalo = caloFromP + caloFromC + caloFromF;

    BigDecimal pPct = totalMacroCalo > 0 ? BigDecimal.valueOf((caloFromP * 100.0) / totalMacroCalo).setScale(1, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    BigDecimal cPct = totalMacroCalo > 0 ? BigDecimal.valueOf((caloFromC * 100.0) / totalMacroCalo).setScale(1, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    BigDecimal fPct = totalMacroCalo > 0 ? BigDecimal.valueOf((caloFromF * 100.0) / totalMacroCalo).setScale(1, RoundingMode.HALF_UP) : BigDecimal.ZERO;

    return new MacrosReportResponse(
        period != null ? period.toUpperCase() : "WEEK",
        start, end, avgP, avgC, avgF, pPct, cPct, fPct, timeSeries
    );
  }

  @Override
  public WeightReportResponse getWeightReport(UUID userId, String period, LocalDate endDate) {
    int days = resolveDays(period);
    LocalDate end = endDate != null ? endDate : LocalDate.now();
    LocalDate start = end.minusDays(days - 1);

    UserHealthProfile profile = userHealthProfileRepository.findByUserId(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.PROFILE_NOT_FOUND, "Profile not found"));

    List<DailySummary> summaries = dailySummaryRepository.findByUserIdAndSummaryDateBetweenOrderBySummaryDateAsc(userId, start, end);
    Map<LocalDate, DailySummary> summaryMap = summaries.stream()
        .collect(Collectors.toMap(DailySummary::getSummaryDate, Function.identity(), (a, b) -> a));

    List<WeightReportResponse.Item> timeSeries = new ArrayList<>();
    BigDecimal lastKnownWeight = profile.getWeightKg();
    BigDecimal startWeight = null;
    BigDecimal currentWeight = null;

    for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
      DailySummary s = summaryMap.get(d);
      if (s != null && s.getWeightKg() != null) {
        lastKnownWeight = s.getWeightKg();
      }

      if (startWeight == null && lastKnownWeight != null) {
        startWeight = lastKnownWeight;
      }
      currentWeight = lastKnownWeight;

      timeSeries.add(new WeightReportResponse.Item(d, lastKnownWeight, profile.getTargetWeightKg()));
    }

    BigDecimal totalChange = (currentWeight != null && startWeight != null)
        ? currentWeight.subtract(startWeight)
        : BigDecimal.ZERO;

    return new WeightReportResponse(
        period != null ? period.toUpperCase() : "MONTH",
        startWeight, currentWeight, profile.getTargetWeightKg(), totalChange, timeSeries
    );
  }

  @Override
  public WeightForecastResponse getWeightForecast(UUID userId) {
    UserHealthProfile profile = userHealthProfileRepository.findByUserId(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.PROFILE_NOT_FOUND, "Profile not found"));

    // Fetch active daily summaries from the past 45 days
    LocalDate start = LocalDate.now().minusDays(45);
    LocalDate end = LocalDate.now();
    List<DailySummary> summaries = dailySummaryRepository.findByUserIdAndSummaryDateBetweenOrderBySummaryDateAsc(userId, start, end);
    List<DailySummary> activeSummaries = summaries.stream()
        .filter(s -> s.getCaloriesConsumed() > 0)
        .toList();

    return weightForecastService.calculateForecast(
        profile.getTdee(),
        profile.getWeightKg(),
        profile.getTargetWeightKg(),
        profile.getGoalType(),
        activeSummaries
    );
  }

  @Override
  public PeriodSummaryReportResponse getPeriodSummary(UUID userId, LocalDate startDate, LocalDate endDate) {
    LocalDate start = startDate != null ? startDate : LocalDate.now().minusDays(6);
    LocalDate end = endDate != null ? endDate : LocalDate.now();

    List<DailySummary> summaries = dailySummaryRepository.findByUserIdAndSummaryDateBetweenOrderBySummaryDateAsc(userId, start, end);

    int totalDays = (int) (end.toEpochDay() - start.toEpochDay() + 1);
    int activeDays = 0;
    int goalMetDays = 0;
    int totalConsumed = 0;
    int totalBurned = 0;
    long totalSteps = 0;
    int totalWater = 0;
    BigDecimal totalP = BigDecimal.ZERO;
    BigDecimal totalC = BigDecimal.ZERO;
    BigDecimal totalF = BigDecimal.ZERO;

    for (DailySummary s : summaries) {
      if (s.getCaloriesConsumed() > 0) {
        activeDays++;
        totalConsumed += s.getCaloriesConsumed();
        totalBurned += s.getCaloriesBurned();
        totalSteps += s.getSteps();
        totalWater += s.getWaterMl();
        totalP = totalP.add(s.getProteinG());
        totalC = totalC.add(s.getCarbsG());
        totalF = totalF.add(s.getFatG());
        if (s.isGoalAchieved()) {
          goalMetDays++;
        }
      }
    }

    double compliance = activeDays > 0 ? (goalMetDays * 100.0) / activeDays : 0.0;
    int avgConsumed = activeDays > 0 ? totalConsumed / activeDays : 0;
    int avgSteps = activeDays > 0 ? (int) (totalSteps / activeDays) : 0;
    int avgWater = activeDays > 0 ? totalWater / activeDays : 0;
    BigDecimal avgP = activeDays > 0 ? totalP.divide(BigDecimal.valueOf(activeDays), 1, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    BigDecimal avgC = activeDays > 0 ? totalC.divide(BigDecimal.valueOf(activeDays), 1, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    BigDecimal avgF = activeDays > 0 ? totalF.divide(BigDecimal.valueOf(activeDays), 1, RoundingMode.HALF_UP) : BigDecimal.ZERO;

    return new PeriodSummaryReportResponse(
        start, end, totalDays, activeDays, Math.round(compliance * 10.0) / 10.0,
        totalConsumed, avgConsumed, totalBurned, totalSteps, avgSteps, avgWater,
        avgP, avgC, avgF
    );
  }

  private int resolveDays(String period) {
    if (period == null) return 7;
    return switch (period.trim().toUpperCase()) {
      case "MONTH" -> 30;
      case "3_MONTHS" -> 90;
      case "YEAR" -> 365;
      default -> 7; // WEEK
    };
  }

  private double calcPct(BigDecimal consumed, BigDecimal target) {
    if (target == null || target.compareTo(BigDecimal.ZERO) <= 0) return 0.0;
    if (consumed == null) return 0.0;
    double pct = consumed.divide(target, 4, RoundingMode.HALF_UP).doubleValue() * 100.0;
    return Math.round(pct * 10.0) / 10.0;
  }
}
