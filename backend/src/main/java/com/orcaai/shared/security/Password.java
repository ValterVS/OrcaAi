package com.orcaai.shared.security;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;

/**
 * Length-based policy (no composition rules): 12 to 64 characters. BCrypt only uses the first 72
 * bytes, so longer UTF-8 encodings are rejected instead of being silently truncated.
 */
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = Password.Validator.class)
public @interface Password {

    int MIN_LENGTH = 12;
    int MAX_LENGTH = 64;
    int MAX_BYTES = 72;

    String message() default "A senha deve ter entre 12 e 64 caracteres.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<Password, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null) {
                return false;
            }
            int length = value.codePointCount(0, value.length());
            return length >= MIN_LENGTH
                    && length <= MAX_LENGTH
                    && value.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES;
        }
    }
}
