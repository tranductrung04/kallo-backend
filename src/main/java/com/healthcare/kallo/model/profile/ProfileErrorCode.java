package com.healthcare.kallo.model.profile;

public enum ProfileErrorCode {
  PROFILE_NOT_FOUND("PRF_404", "Hồ sơ người dùng không tồn tại"),
  UNDERAGE_GOAL_RESTRICTION("PRF_400_1", "Người dưới 18 tuổi chỉ được chọn giữ cân"),
  UNSAFE_TARGET_BMI("PRF_400_2", "Mục tiêu cân nặng khiến BMI nhỏ hơn 18.5"),
  INVALID_WEEKLY_RATE("PRF_400_3", "Tốc độ thay đổi cân nặng không hợp lệ"),
  INVALID_INGREDIENT("PRF_400_4", "Nguyên liệu dị ứng không tồn tại hoặc đã bị vô hiệu hóa"),
  INVALID_CONDITION("PRF_400_5", "Bệnh mạn tính không tồn tại trong hệ thống");

  private final String code;
  private final String message;

  ProfileErrorCode(String code, String message) {
    this.code = code;
    this.message = message;
  }

  public String getCode() {
    return code;
  }

  public String getMessage() {
    return message;
  }
}
