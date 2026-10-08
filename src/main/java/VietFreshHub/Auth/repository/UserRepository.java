package VietFreshHub.Auth.repository;

import VietFreshHub.Auth.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmailIgnoreCase(String email);
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByPhone(String phone);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select u from User u
            join u.userRoles ur
            join ur.role r
            where u.status = 'ACTIVE' and r.roleName = :roleName
            order by u.userId asc
            """)
    List<User> findActiveUsersByRoleNameForUpdate(@Param("roleName") String roleName);
}
