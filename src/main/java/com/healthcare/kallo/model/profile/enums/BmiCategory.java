package com.healthcare.kallo.model.profile.enums;

public enum BmiCategory {
  UNDERWEIGHT("Thiếu cân"),
  NORMAL("Bình thường"),
  OVERWEIGHT("Thừa cân"),
  OBESE("Béo phì");

  private final String descriptionVi;

  BmiCategory(String descriptionVi) {
    this.descriptionVi = descriptionVi;
  }

  public String getDescriptionVi() {
    return descriptionVi;
  }
}
