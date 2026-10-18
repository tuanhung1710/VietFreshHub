package VietFreshHub.delivery.repository;

import VietFreshHub.delivery.entity.Delivery;
import VietFreshHub.delivery.entity.DeliveryStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeliveryRepository extends JpaRepository<Delivery, Long>, JpaSpecificationExecutor<Delivery> {

    Optional<Delivery> findByShopOrder_ShopOrderId(Long shopOrderId);

    @Override
    @EntityGraph(attributePaths = {"shopOrder", "shopOrder.order", "shopOrder.shop"})
    Page<Delivery> findAll(Specification<Delivery> specification, Pageable pageable);

    @EntityGraph(attributePaths = {"shopOrder", "shopOrder.order", "shopOrder.shop"})
    Optional<Delivery> findByDeliveryId(Long deliveryId);

    @EntityGraph(attributePaths = {"shopOrder", "shopOrder.order", "shopOrder.shop"})
    Optional<Delivery> findByDeliveryIdAndShopOrder_ShopId(Long deliveryId, Long shopId);

    @Query("""
            select d.deliveryId from Delivery d
            where d.status = :status
              and d.shopOrder.status = 'READY_FOR_DELIVERY'
              and d.redeliveryRequestedAt is null and d.redeliveryScheduledAt is null
            order by d.createdAt asc, d.deliveryId asc
            """)
    List<Long> findUnassignedDeliveryIds(@Param("status") DeliveryStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Delivery> findForUpdateByDeliveryId(Long deliveryId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Delivery> findForUpdateByDeliveryIdAndShopOrder_ShopId(Long deliveryId, Long shopId);
}
