package com.healthcare.kallo.model.act;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "activity_types")
@Getter
@Setter
public class ActivityType {
  @Id
  @Column(length = 40)
  private String code;

  @Column(name = "name_vi", nullable = false, length = 100)
  private String nameVi;

  @Column(nullable = false, length = 20)
  private String category;

  @Column(name = "met_light", precision = 4, scale = 1)
  private BigDecimal metLight;

  @Column(name = "met_moderate", nullable = false, precision = 4, scale = 1)
  private BigDecimal metModerate;

  @Column(name = "met_vigorous", precision = 4, scale = 1)
  private BigDecimal metVigorous;

  @Column(name = "has_distance", nullable = false)
  private boolean hasDistance;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(nullable = false)
  private boolean active;
}
