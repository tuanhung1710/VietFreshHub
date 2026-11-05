package VietFreshHub.coupon.repository;

import VietFreshHub.coupon.entity.CouponUsage;
import VietFreshHub.order.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CouponUsageRepository
        extends JpaRepository<CouponUsage, Long> {

    // Canceled orders stay in history but no longer consume coupon quota.
    @Query("SELECT COUNT(u) FROM CouponUsage u WHERE u.coupon.couponId = :couponId AND u.order.status <> :cancelledStatus")
    long countActiveByCouponId(@Param("couponId") Long couponId,
                               @Param("cancelledStatus") Order.Status cancelledStatus);

    @Query("SELECT COUNT(u) FROM CouponUsage u WHERE u.coupon.couponId = :couponId AND u.user.userId = :userId AND u.order.status <> :cancelledStatus")
    long countActiveByCouponIdAndUserId(@Param("couponId") Long couponId,
                                        @Param("userId") Long userId,
                                        @Param("cancelledStatus") Order.Status cancelledStatus);

    // Kiểm tra coupon đã được ghi nhận cho đơn này chưa.
    boolean existsByCoupon_CouponIdAndOrder_OrderId(Long couponId, Long orderId);

    // Lịch sử sử dụng của một coupon thuộc shop đang quản lý.
    @EntityGraph(attributePaths = {"coupon", "user", "order"})
    Page<CouponUsage>
    findByCoupon_CouponIdAndCoupon_Promotion_ShopIdOrderByUsedAtDescCouponUsageIdDesc(Long couponId, Long shopId, Pageable pageable);
}
