package VietFreshHub.auth.exception;

import lombok.Getter;

@Getter
public class RegistrationException extends RuntimeException {

    private final String field;

    public RegistrationException(String field, String message) {
        super(message);
        this.field = field;
    }
}
