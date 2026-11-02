package VietFreshHub.auth.repository;

import VietFreshHub.auth.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Integer> {

    Optional<Role> findByRoleName(String roleName);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Role r where r.roleName = :name")
    Optional<Role> findForUpdateByRoleName(@Param("name") String roleName);
}
