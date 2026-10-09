package VietFreshHub.auth.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class PendingRegistration implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String fullName;
    private String email;
    private String phone;
    private String passwordHash;

    private String otpHash;
    private LocalDateTime expiresAt;
    private LocalDateTime lastSentAt;
    private int attemptCount;
}