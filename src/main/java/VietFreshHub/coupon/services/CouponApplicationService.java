package VietFreshHub.coupon.services;

import VietFreshHub.coupon.dto.CouponApplicationResponse;
import VietFreshHub.coupon.dto.CouponUsageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface CouponApplicationService {

    // Kiểm tra mã và tính thử mức giảm từ các dòng giỏ hàng.
    // Chưa ghi nhận lượt sử dụng.
    CouponApplicationResponse preview(
            Long customerId,
            String code,
            List<Long> cartItemIds
    );

    // Kiểm tra lại coupon trên dữ liệu đơn đã tạo,
    // áp dụng giảm giá và ghi nhận lượt sử dụng.
    CouponApplicationResponse applyToOrder(
            Long customerId,
            Long orderId,
            String code
    );

    // Shop xem lịch sử sử dụng một coupon của mình.
    Page<CouponUsageResponse> getUsageHistory(
            Long shopId,
            Long couponId,
            Pageable pageable
    );
}