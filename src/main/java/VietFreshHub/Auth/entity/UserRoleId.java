package VietFreshHub.Auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
@Embeddable
public class UserRoleId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "role_id")
    private Integer roleId;
}
