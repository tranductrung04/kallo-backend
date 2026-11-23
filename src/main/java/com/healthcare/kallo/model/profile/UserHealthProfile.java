package com.healthcare.kallo.model.profile;

import com.healthcare.kallo.model.auth.User;
import com.healthcare.kallo.model.profile.enums.ActivityLevel;
import com.healthcare.kallo.model.profile.enums.BmiCategory;
import com.healthcare.kallo.model.profile.enums.Gender;
import com.healthcare.kallo.model.profile.enums.GoalType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "user_health_profiles")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class UserHealthProfile {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(
      name = "user_id",
      nullable = false,
      unique = true,
      foreignKey = @ForeignKey(name = "fk_health_profiles_user"))
  private User user;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Gender gender;

  @Column(name = "birth_date", nullable = false)
  private LocalDate birthDate;

  @Column(name = "height_cm", nullable = false, precision = 5, scale = 1)
  private BigDecimal heightCm;

  @Column(name = "weight_kg", nullable = false, precision = 5, scale = 1)
  private BigDecimal weightKg;

  @Enumerated(EnumType.STRING)
  @Column(name = "activity_level", nullable = false, length = 30)
  private ActivityLevel activityLevel;

  @Enumerated(EnumType.STRING)
  @Column(name = "goal_type", nullable = false, length = 30)
  private GoalType goalType;

  @Column(name = "target_weight_kg", precision = 5, scale = 1)
  private BigDecimal targetWeightKg;

  @Column(name = "weekly_rate_kg", nullable = false, precision = 4, scale = 2)
  private BigDecimal weeklyRateKg;

  @Column(nullable = false, precision = 4, scale = 1)
  private BigDecimal bmi;

  @Enumerated(EnumType.STRING)
  @Column(name = "bmi_category", nullable = false, length = 30)
  private BmiCategory bmiCategory;

  @Column(nullable = false)
  private int bmr;

  @Column(nullable = false)
  private int tdee;

  @Column(name = "daily_calorie_goal", nullable = false)
  private int dailyCalorieGoal;

  @Column(name = "safety_clamped", nullable = false)
  private boolean safetyClamped;

  @Column(name = "protein_goal_g", nullable = false, precision = 5, scale = 1)
  private BigDecimal proteinGoalG;

  @Column(name = "carbs_goal_g", nullable = false, precision = 5, scale = 1)
  private BigDecimal carbsGoalG;

  @Column(name = "fat_goal_g", nullable = false, precision = 5, scale = 1)
  private BigDecimal fatGoalG;

  @Column(name = "protein_pct", nullable = false, precision = 4, scale = 1)
  private BigDecimal proteinPct;

  @Column(name = "carbs_pct", nullable = false, precision = 4, scale = 1)
  private BigDecimal carbsPct;

  @Column(name = "fat_pct", nullable = false, precision = 4, scale = 1)
  private BigDecimal fatPct;

  @Column(name = "custom_macro", nullable = false)
  private boolean customMacro;

  @Column(name = "water_goal_ml", nullable = false)
  private int waterGoalMl;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private List<String> allergies = new ArrayList<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "preference_vector", columnDefinition = "jsonb")
  private List<Double> preferenceVector = new ArrayList<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "chronic_conditions", columnDefinition = "jsonb")
  private List<String> chronicConditions = new ArrayList<>();

  @Column(name = "recalculation_recommended", nullable = false)
  private boolean recalculationRecommended;

  @Column(name = "recommended_tdee")
  private Integer recommendedTdee;

  @Column(name = "recommended_daily_calorie_goal")
  private Integer recommendedDailyCalorieGoal;

  @Column(name = "latest_logged_weight_kg", precision = 5, scale = 1)
  private BigDecimal latestLoggedWeightKg;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public Gender getGender() {
    return gender;
  }

  public void setGender(Gender gender) {
    this.gender = gender;
  }

  public LocalDate getBirthDate() {
    return birthDate;
  }

  public void setBirthDate(LocalDate birthDate) {
    this.birthDate = birthDate;
  }

  public BigDecimal getHeightCm() {
    return heightCm;
  }

  public void setHeightCm(BigDecimal heightCm) {
    this.heightCm = heightCm;
  }

  public BigDecimal getWeightKg() {
    return weightKg;
  }

  public void setWeightKg(BigDecimal weightKg) {
    this.weightKg = weightKg;
  }

  public ActivityLevel getActivityLevel() {
    return activityLevel;
  }

  public void setActivityLevel(ActivityLevel activityLevel) {
    this.activityLevel = activityLevel;
  }

  public GoalType getGoalType() {
    return goalType;
  }

  public void setGoalType(GoalType goalType) {
    this.goalType = goalType;
  }

  public BigDecimal getTargetWeightKg() {
    return targetWeightKg;
  }

  public void setTargetWeightKg(BigDecimal targetWeightKg) {
    this.targetWeightKg = targetWeightKg;
  }

  public BigDecimal getWeeklyRateKg() {
    return weeklyRateKg;
  }

  public void setWeeklyRateKg(BigDecimal weeklyRateKg) {
    this.weeklyRateKg = weeklyRateKg;
  }

  public BigDecimal getBmi() {
    return bmi;
  }

  public void setBmi(BigDecimal bmi) {
    this.bmi = bmi;
  }

  public BmiCategory getBmiCategory() {
    return bmiCategory;
  }

  public void setBmiCategory(BmiCategory bmiCategory) {
    this.bmiCategory = bmiCategory;
  }

  public int getBmr() {
    return bmr;
  }

  public void setBmr(int bmr) {
    this.bmr = bmr;
  }

  public int getTdee() {
    return tdee;
  }

  public void setTdee(int tdee) {
    this.tdee = tdee;
  }

  public int getDailyCalorieGoal() {
    return dailyCalorieGoal;
  }

  public void setDailyCalorieGoal(int dailyCalorieGoal) {
    this.dailyCalorieGoal = dailyCalorieGoal;
  }

  public boolean isSafetyClamped() {
    return safetyClamped;
  }

  public void setSafetyClamped(boolean safetyClamped) {
    this.safetyClamped = safetyClamped;
  }

  public BigDecimal getProteinGoalG() {
    return proteinGoalG;
  }

  public void setProteinGoalG(BigDecimal proteinGoalG) {
    this.proteinGoalG = proteinGoalG;
  }

  public BigDecimal getCarbsGoalG() {
    return carbsGoalG;
  }

  public void setCarbsGoalG(BigDecimal carbsGoalG) {
    this.carbsGoalG = carbsGoalG;
  }

  public BigDecimal getFatGoalG() {
    return fatGoalG;
  }

  public void setFatGoalG(BigDecimal fatGoalG) {
    this.fatGoalG = fatGoalG;
  }

  public BigDecimal getProteinPct() {
    return proteinPct;
  }

  public void setProteinPct(BigDecimal proteinPct) {
    this.proteinPct = proteinPct;
  }

  public BigDecimal getCarbsPct() {
    return carbsPct;
  }

  public void setCarbsPct(BigDecimal carbsPct) {
    this.carbsPct = carbsPct;
  }

  public BigDecimal getFatPct() {
    return fatPct;
  }

  public void setFatPct(BigDecimal fatPct) {
    this.fatPct = fatPct;
  }

  public boolean isCustomMacro() {
    return customMacro;
  }

  public void setCustomMacro(boolean customMacro) {
    this.customMacro = customMacro;
  }

  public int getWaterGoalMl() {
    return waterGoalMl;
  }

  public void setWaterGoalMl(int waterGoalMl) {
    this.waterGoalMl = waterGoalMl;
  }

  public List<String> getAllergies() {
    return allergies;
  }

  public void setAllergies(List<String> allergies) {
    this.allergies = allergies;
  }

  public boolean isRecalculationRecommended() {
    return recalculationRecommended;
  }

  public void setRecalculationRecommended(boolean recalculationRecommended) {
    this.recalculationRecommended = recalculationRecommended;
  }

  public Integer getRecommendedTdee() {
    return recommendedTdee;
  }

  public void setRecommendedTdee(Integer recommendedTdee) {
    this.recommendedTdee = recommendedTdee;
  }

  public Integer getRecommendedDailyCalorieGoal() {
    return recommendedDailyCalorieGoal;
  }

  public void setRecommendedDailyCalorieGoal(Integer recommendedDailyCalorieGoal) {
    this.recommendedDailyCalorieGoal = recommendedDailyCalorieGoal;
  }

  public BigDecimal getLatestLoggedWeightKg() {
    return latestLoggedWeightKg;
  }

  public void setLatestLoggedWeightKg(BigDecimal latestLoggedWeightKg) {
    this.latestLoggedWeightKg = latestLoggedWeightKg;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
