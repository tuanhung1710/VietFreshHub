package VietFreshHub.checkout.repository;

import VietFreshHub.auth.entity.Address;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/** Read-only address access. Address CRUD remains TV1's responsibility. */
public interface CheckoutAddressRepository extends Repository<Address,Long> {
    List<Address> findByUserUserIdOrderByIsDefaultDescAddressIdAsc(Long userId);
    Optional<Address> findByAddressIdAndUserUserId(Long addressId,Long userId);
}
