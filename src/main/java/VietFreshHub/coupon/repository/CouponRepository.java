package VietFreshHub.coupon.repository;

import VietFreshHub.coupon.entity.Coupon;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {
    @EntityGraph(attributePaths = "promotion")
    List<Coupon> findByPromotion_ShopIdOrderByCouponIdDesc(Long shopId);

    @EntityGraph(attributePaths = "promotion")
    Optional<Coupon> findByCouponIdAndPromotion_ShopId(Long couponId, Long shopId);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndCouponIdNot(String code, Long couponId);

    // Tra cứu khi khách nhập mã để xem trước giảm giá.
    @EntityGraph(attributePaths = "promotion")
    Optional<Coupon> findByCodeIgnoreCase(String code);

    // Khóa coupon khi xác nhận đặt hàng.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT c
        FROM Coupon c
        WHERE LOWER(c.code) = LOWER(:code)
        """)
    Optional<Coupon> findByCodeForUpdate(
            @Param("code") String code
    );

}
