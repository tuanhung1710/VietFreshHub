package VietFreshHub.review.repository;

import VietFreshHub.review.entity.ReviewReport;
import VietFreshHub.review.entity.ReviewReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

public interface ReviewReportRepository extends JpaRepository<ReviewReport, Long> {
    @EntityGraph(attributePaths = {"review", "review.user", "review.product", "review.shop", "reporter"})
    Page<ReviewReport> findByStatusOrderByCreatedAtAsc(ReviewReportStatus status, Pageable pageable);

    void deleteByReview_ReviewId(Long reviewId);
}
