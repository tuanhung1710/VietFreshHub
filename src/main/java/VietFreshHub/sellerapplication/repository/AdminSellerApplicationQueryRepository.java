package VietFreshHub.sellerapplication.repository;

import VietFreshHub.sellerapplication.entity.SellerApplication;
import VietFreshHub.sellerapplication.entity.SellerApplicationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

/** Read and locking queries kept separate so the existing SellerApplicationRepository need not be replaced. */
public interface AdminSellerApplicationQueryRepository extends Repository<SellerApplication, Long> {

    @EntityGraph(attributePaths = {"user", "reviewedBy"})
    @Query("select a from SellerApplication a order by a.submittedAt desc")
    List<SellerApplication> findAllForAdminOrderBySubmittedAtDesc();

    @EntityGraph(attributePaths = {"user", "reviewedBy"})
    @Query("select a from SellerApplication a where a.status = :status order by a.submittedAt desc")
    List<SellerApplication> findAllForAdminByStatusOrderBySubmittedAtDesc(
            @Param("status") SellerApplicationStatus status
    );

    @Query("select count(a) from SellerApplication a where a.status = :status")
    long countForStatus(@Param("status") SellerApplicationStatus status);

    @EntityGraph(attributePaths = {"user", "reviewedBy"})
    @Query("select a from SellerApplication a where a.applicationId = :applicationId")
    Optional<SellerApplication> findForAdminById(@Param("applicationId") Long applicationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from SellerApplication a where a.applicationId = :applicationId")
    Optional<SellerApplication> findByIdForUpdate(@Param("applicationId") Long applicationId);
}
