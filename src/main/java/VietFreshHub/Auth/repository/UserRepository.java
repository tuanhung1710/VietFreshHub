package VietFreshHub.Auth.repository;

import VietFreshHub.Auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u where u.userId = :userId")
    Optional<User> findForCartUpdate(@org.springframework.data.repository.query.Param("userId") Long userId);

    boolean existsByEmailIgnoreCase(String email);
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByPhone(String phone);
}
