package VietFreshHub.review.repository;

import VietFreshHub.review.entity.ReviewReport;
import VietFreshHub.review.entity.ReviewReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface ReviewReportRepository extends JpaRepository<ReviewReport, Long> {
    @EntityGraph(attributePaths = {"review", "review.user", "review.product", "review.shop", "reporter"})
    Page<ReviewReport> findByStatusOrderByCreatedAtAsc(ReviewReportStatus status, Pageable pageable);

    boolean existsByReview_ReviewIdAndReporter_UserId(Long reviewId, Long reporterUserId);
    boolean existsByReview_ReviewId(Long reviewId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select report from ReviewReport report where report.reportId = :reportId")
    Optional<ReviewReport> findByReportIdForUpdate(@Param("reportId") Long reportId);
}
