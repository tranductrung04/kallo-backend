package com.healthcare.kallo.model.profile;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "user_chronic_conditions")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class UserChronicCondition {

  @EmbeddedId
  private UserChronicConditionId id = new UserChronicConditionId();

  @ManyToOne(fetch = FetchType.LAZY)
  @MapsId("userId")
  @JoinColumn(name = "user_id", nullable = false)
  private UserHealthProfile profile;

  @ManyToOne(fetch = FetchType.LAZY)
  @MapsId("conditionId")
  @JoinColumn(name = "condition_id", nullable = false)
  private ChronicCondition condition;

  @Column(name = "diagnosed_at")
  private LocalDate diagnosedAt;

  @Column(name = "note", columnDefinition = "TEXT")
  private String note;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
