package VietFreshHub.Auth.repository;

import VietFreshHub.Auth.entity.UserRole;
import VietFreshHub.Auth.entity.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserRoleRepository
        extends JpaRepository<UserRole, UserRoleId> {
    List<UserRole> findAllByUser_UserId(Long userId);
    @Query("""
       select r.roleName
       from UserRole ur
       join ur.role r
       where ur.user.userId = :userId
       """)
    List<String> findRoleNamesByUserId(@Param("userId") Long userId);
}
