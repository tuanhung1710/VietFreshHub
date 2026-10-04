package VietFreshHub.Order.repository;

import VietFreshHub.Order.entity.OrderAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderAddressRepository extends JpaRepository<OrderAddress, Long> {

    Optional<OrderAddress> findByOrderId(Long orderId);
}
