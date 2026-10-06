package VietFreshHub.Auth.repository;

import VietFreshHub.Auth.entity.UserRole;
import VietFreshHub.Auth.entity.UserRoleId;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Inserts the role link without depending on the field names of the composite-key entity. */
public interface UserRoleGrantRepository extends Repository<UserRole, UserRoleId> {

    @Modifying
    @Query(value = "INSERT INTO dbo.user_roles (user_id, role_id, assigned_at) " +
                   "SELECT :userId, :roleId, SYSUTCDATETIME() " +
                   "WHERE NOT EXISTS (" +
                   "    SELECT 1 FROM dbo.user_roles WITH (UPDLOCK, HOLDLOCK) " +
                   "    WHERE user_id = :userId AND role_id = :roleId" +
                   ")", nativeQuery = true)
    int grantIfMissing(@Param("userId") Long userId, @Param("roleId") Integer roleId);
}
