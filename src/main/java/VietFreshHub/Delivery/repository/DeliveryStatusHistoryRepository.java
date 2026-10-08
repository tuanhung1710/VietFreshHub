package VietFreshHub.Delivery.repository;

import VietFreshHub.Delivery.entity.DeliveryStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeliveryStatusHistoryRepository extends JpaRepository<DeliveryStatusHistory, Long> {
}
