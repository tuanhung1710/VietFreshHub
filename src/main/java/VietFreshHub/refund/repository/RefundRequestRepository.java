package VietFreshHub.refund.repository;

import VietFreshHub.refund.entity.RefundRequest;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RefundRequestRepository extends JpaRepository<RefundRequest, Long> {

    Optional<RefundRequest> findByOrderOrderId(Long orderId);

    @EntityGraph(attributePaths = "order")
    List<RefundRequest> findBySubmittedAtGreaterThanEqualAndSubmittedAtLessThanOrderBySubmittedAtAscRefundRequestIdAsc(
            LocalDateTime startUtc, LocalDateTime endUtc);
}
