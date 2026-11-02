package VietFreshHub.coupon.repository;

import VietFreshHub.coupon.entity.CouponUsage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponUsageRepository
        extends JpaRepository<CouponUsage, Long> {

    // Tổng lượt sử dụng của coupon.
    long countByCoupon_CouponId(Long couponId);

    // Số lượt một khách đã dùng coupon.
    long countByCoupon_CouponIdAndUser_UserId(Long couponId, Long userId);

    // Kiểm tra coupon đã được ghi nhận cho đơn này chưa.
    boolean existsByCoupon_CouponIdAndOrder_OrderId(Long couponId, Long orderId);

    // Lịch sử sử dụng của một coupon thuộc shop đang quản lý.
    @EntityGraph(attributePaths = {"coupon", "user", "order"})
    Page<CouponUsage>
    findByCoupon_CouponIdAndCoupon_Promotion_ShopIdOrderByUsedAtDescCouponUsageIdDesc(Long couponId, Long shopId, Pageable pageable);
}