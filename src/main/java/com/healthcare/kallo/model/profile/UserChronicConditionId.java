package com.healthcare.kallo.model.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserChronicConditionId implements Serializable {

  @Column(name = "user_id")
  private UUID userId;

  @Column(name = "condition_id")
  private UUID conditionId;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    UserChronicConditionId that = (UserChronicConditionId) o;
    return Objects.equals(userId, that.userId) && Objects.equals(conditionId, that.conditionId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(userId, conditionId);
  }
}
