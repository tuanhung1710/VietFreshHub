package VietFreshHub.auth.repository;

import VietFreshHub.auth.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    boolean existsByEmailIgnoreCase(String email);
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByPhone(String phone);
    boolean existsByPhoneAndUserIdNot(String phone, Long userId);
    long countByStatus(String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.userId = :userId")
    Optional<User> findByUserIdForUpdate(@Param("userId") Long userId);

    @Query("""
            select count(distinct u.userId)
            from User u
            join u.userRoles ur
            join ur.role r
            where u.status = :status
              and r.roleName = :roleName
              and u.userId <> :excludedUserId
            """)
    long countOtherUsersWithRoleAndStatus(
            @Param("status") String status,
            @Param("roleName") String roleName,
            @Param("excludedUserId") Long excludedUserId
    );
}
