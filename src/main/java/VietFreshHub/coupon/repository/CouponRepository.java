package VietFreshHub.coupon.repository;

import VietFreshHub.coupon.entity.Coupon;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {
    @EntityGraph(attributePaths = "promotion")
    List<Coupon> findByPromotion_ShopIdOrderByCouponIdDesc(Long shopId);

    @EntityGraph(attributePaths = "promotion")
    Optional<Coupon> findByCouponIdAndPromotion_ShopId(Long couponId, Long shopId);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndCouponIdNot(String code, Long couponId);
}
