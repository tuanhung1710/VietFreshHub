package VietFreshHub.refund.repository;

import VietFreshHub.refund.entity.RefundRequest;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RefundRequestRepository extends JpaRepository<RefundRequest, Long> {

    Optional<RefundRequest> findByOrderOrderIdAndShopOrderIsNull(Long orderId);

    Optional<RefundRequest> findByShopOrderShopOrderId(Long shopOrderId);

    boolean existsByOrderOrderIdAndShopOrderIsNotNull(Long orderId);

    @Query("select coalesce(sum(r.refundAmount),0) from RefundRequest r where r.order.orderId=:orderId")
    BigDecimal sumRefundAmount(@Param("orderId") Long orderId);

    @EntityGraph(attributePaths = {"order", "shopOrder"})
    List<RefundRequest> findBySubmittedAtGreaterThanEqualAndSubmittedAtLessThanOrderBySubmittedAtAscRefundRequestIdAsc(
            LocalDateTime startUtc, LocalDateTime endUtc);
}
