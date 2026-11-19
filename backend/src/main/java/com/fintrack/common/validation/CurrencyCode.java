package com.fintrack.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** The value must be an upper-case ISO 4217 currency code such as EUR. Null is valid; combine with @NotNull. */
@Documented
@Constraint(validatedBy = CurrencyCodeValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrencyCode {

    String message() default "Must be a valid ISO 4217 currency code such as EUR";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
