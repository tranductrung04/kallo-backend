package com.healthcare.kallo.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = TimezoneValidator.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidTimezone {
  String message() default "must be a valid IANA timezone";
  Class<?>[] groups() default {};
  Class<? extends Payload>[] payload() default {};
}
