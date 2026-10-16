package VietFreshHub.coupon.services;

import VietFreshHub.coupon.dto.CouponForm;
import VietFreshHub.coupon.dto.CouponResponse;

import java.util.List;

public interface CouponService {

    List<CouponResponse> getByShop(Long shopId);

    CouponResponse getById(Long couponId, Long shopId);

    CouponResponse create(Long shopId, CouponForm form);

    CouponResponse update(
            Long couponId,
            Long shopId,
            CouponForm form
    );

    void activate(Long couponId, Long shopId);

    void deactivate(Long couponId, Long shopId);

    void disable(Long couponId, Long shopId);
}