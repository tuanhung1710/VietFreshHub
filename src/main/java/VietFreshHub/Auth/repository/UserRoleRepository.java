package VietFreshHub.auth.repository;

import VietFreshHub.auth.entity.UserRole;
import VietFreshHub.auth.entity.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;

public interface UserRoleRepository
        extends JpaRepository<UserRole, UserRoleId> {
    List<UserRole> findAllByUser_UserId(Long userId);

    @Query("""
       select ur.user.userId as userId, r.roleId as roleId, r.roleName as roleName
       from UserRole ur
       join ur.role r
       where ur.user.userId in :userIds
       order by r.roleName
       """)
    List<UserRoleRow> findRoleRowsByUserIds(@Param("userIds") Collection<Long> userIds);

    @Query("""
       select r.roleName
       from UserRole ur
       join ur.role r
       where ur.user.userId = :userId
       """)
    List<String> findRoleNamesByUserId(@Param("userId") Long userId);

    interface UserRoleRow {
        Long getUserId();
        Integer getRoleId();
        String getRoleName();
    }
}
