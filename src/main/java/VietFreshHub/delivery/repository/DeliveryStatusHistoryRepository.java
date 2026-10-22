package VietFreshHub.delivery.repository;

import VietFreshHub.delivery.entity.DeliveryStatusHistory;
import VietFreshHub.delivery.entity.DeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeliveryStatusHistoryRepository extends JpaRepository<DeliveryStatusHistory, Long> {

    List<DeliveryStatusHistory> findByDelivery_DeliveryIdOrderByCreatedAtAscHistoryIdAsc(Long deliveryId);

    boolean existsByDelivery_DeliveryIdAndStatusAndNoteAndChangedByIsNotNull(
            Long deliveryId, DeliveryStatus status, String note);

    List<DeliveryStatusHistory> findByDelivery_DeliveryIdInAndStatusAndNoteStartingWithAndChangedByIsNotNull(
            List<Long> deliveryIds, DeliveryStatus status, String notePrefix);
}
