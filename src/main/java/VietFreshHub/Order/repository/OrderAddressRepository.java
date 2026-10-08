package VietFreshHub.Order.repository;
import VietFreshHub.Order.entity.OrderAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface OrderAddressRepository extends JpaRepository<OrderAddress,Long> {
    Optional<OrderAddress> findByOrderOrderId(Long orderId);
}
