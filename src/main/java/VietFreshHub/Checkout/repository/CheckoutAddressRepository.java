package VietFreshHub.Checkout.repository;
import VietFreshHub.Auth.entity.Address;
import org.springframework.data.repository.Repository;
import java.util.List;
import java.util.Optional;
/** Read-only address access. Address CRUD remains TV1's responsibility. */
public interface CheckoutAddressRepository extends Repository<Address,Long> {
    List<Address> findByUserUserIdOrderByIsDefaultDescAddressIdAsc(Long userId);
    Optional<Address> findByAddressIdAndUserUserId(Long addressId,Long userId);
}
