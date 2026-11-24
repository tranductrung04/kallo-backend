package com.healthcare.kallo.model.profile;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "user_allergies")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class UserAllergy {

  @EmbeddedId
  private UserAllergyId id = new UserAllergyId();

  @ManyToOne(fetch = FetchType.LAZY)
  @MapsId("userId")
  @JoinColumn(name = "user_id", nullable = false)
  private UserHealthProfile profile;

  @Column(name = "note", columnDefinition = "TEXT")
  private String note;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
