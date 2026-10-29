package VietFreshHub.payout.repository;

import VietFreshHub.payout.entity.ShopPayoutAccount;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ShopPayoutAccountRepository extends JpaRepository<ShopPayoutAccount, Long> {

    @EntityGraph(attributePaths = "shop")
    Optional<ShopPayoutAccount> findByShopId(Long shopId);

    @EntityGraph(attributePaths = "shop")
    List<ShopPayoutAccount> findByShopIdInAndVerifiedTrue(Collection<Long> shopIds);

    @EntityGraph(attributePaths = "shop")
    List<ShopPayoutAccount> findAllByOrderByShopShopNameAsc();
}
