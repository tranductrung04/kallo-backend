package com.healthcare.kallo.model.sys.enums;

public enum NotificationType {
  WATER_REMINDER(Kind.REMINDER),
  MEAL_REMINDER(Kind.REMINDER),
  CALORIE_OVER(Kind.ALERT),
  BADGE_EARNED(Kind.SOCIAL),
  FRIEND_REQUEST(Kind.SOCIAL),
  FRIEND_OVERTAKE(Kind.SOCIAL),
  CHALLENGE_EXPIRING(Kind.SOCIAL),
  CHALLENGE_COMPLETED(Kind.SOCIAL),
  SYSTEM(Kind.SYSTEM);

  private final Kind kind;

  NotificationType(Kind kind) {
    this.kind = kind;
  }

  public Kind kind() {
    return kind;
  }

  public enum Kind {
    REMINDER,
    ALERT,
    SOCIAL,
    SYSTEM
  }
}
