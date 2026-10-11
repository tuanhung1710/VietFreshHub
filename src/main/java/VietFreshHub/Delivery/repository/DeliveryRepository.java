package VietFreshHub.Delivery.repository;

import VietFreshHub.Delivery.entity.Delivery;
import VietFreshHub.Delivery.entity.DeliveryStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DeliveryRepository extends JpaRepository<Delivery, Long> {

    Optional<Delivery> findByShopOrder_ShopOrderId(Long shopOrderId);

    List<Delivery> findByStatusOrderByCreatedAtAscDeliveryIdAsc(DeliveryStatus status);

    @Query("""
            select d.deliveryId from Delivery d
            where d.status = :status
              and d.redeliveryScheduledAt is not null
              and d.redeliveryScheduledAt <= :now
            order by d.redeliveryScheduledAt asc, d.deliveryId asc
            """)
    List<Long> findDueRedeliveryIds(@Param("status") DeliveryStatus status, @Param("now") LocalDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Delivery> findForUpdateByDeliveryId(Long deliveryId);
}
