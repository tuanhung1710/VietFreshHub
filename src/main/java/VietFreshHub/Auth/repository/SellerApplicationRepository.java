package VietFreshHub.Auth.repository;

import VietFreshHub.Auth.entity.SellerApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SellerApplicationRepository
        extends JpaRepository<SellerApplication, Long> {
    List<SellerApplication> findByUser_EmailIgnoreCaseOrderBySubmittedAtDesc(
            String email
    );

    Optional<SellerApplication> findByApplicationIdAndUser_EmailIgnoreCase(
            Long applicationId,
            String email
    );
}