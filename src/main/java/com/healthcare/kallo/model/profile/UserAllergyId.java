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
public class UserAllergyId implements Serializable {

  @Column(name = "user_id")
  private UUID userId;

  @Column(name = "ingredient_id")
  private UUID ingredientId;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    UserAllergyId that = (UserAllergyId) o;
    return Objects.equals(userId, that.userId) && Objects.equals(ingredientId, that.ingredientId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(userId, ingredientId);
  }
}
