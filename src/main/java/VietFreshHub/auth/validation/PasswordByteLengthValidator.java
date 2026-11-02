package VietFreshHub.auth.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

/** BCrypt limits newly encoded passwords by UTF-8 bytes, rather than character count. */
public class PasswordByteLengthValidator implements ConstraintValidator<PasswordByteLength, String> {
    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
