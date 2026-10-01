package VietFreshHub.Auth.repository;

import VietFreshHub.Auth.entity.UserRole;
import VietFreshHub.Auth.entity.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRoleRepository
        extends JpaRepository<UserRole, UserRoleId> {
}
