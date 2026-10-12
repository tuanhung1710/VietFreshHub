package VietFreshHub.auth.repository;

import VietFreshHub.auth.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AddressRepository extends JpaRepository<Address, Long> {

    List<Address> findAllByUser_UserIdOrderByIsDefaultDescAddressIdAsc(Long userId);

    List<Address> findAllByUser_UserIdAndIsDefaultTrue(Long userId);

    Optional<Address> findByAddressIdAndUser_UserId(Long addressId, Long userId);
}
