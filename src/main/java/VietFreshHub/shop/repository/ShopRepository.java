package VietFreshHub.shop.repository;

import VietFreshHub.shop.entity.Shop;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ShopRepository extends JpaRepository<Shop, Long> {

    List<Shop> findAllByStatusOrderByShopNameAsc(String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Shop s where s.shopId = :shopId")
    Optional<Shop> findByShopIdForUpdate(@Param("shopId") Long shopId);
}
