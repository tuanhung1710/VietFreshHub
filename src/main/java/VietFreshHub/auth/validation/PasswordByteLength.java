package VietFreshHub.auth.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordByteLengthValidator.class)
public @interface PasswordByteLength {
    String message() default "Mật khẩu quá dài (tối đa 72 byte; ký tự có dấu có thể chiếm nhiều byte).";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
