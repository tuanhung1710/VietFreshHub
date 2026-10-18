package VietFreshHub.subscription.services;

import VietFreshHub.subscription.dto.ShopSubscriptionResponse;
import VietFreshHub.subscription.dto.SubscriptionPaymentResponse;

import java.util.List;
import java.util.Optional;

public interface ShopSubscriptionService {

    // Gói đăng ký hiện tại còn hiệu lực.
    Optional<ShopSubscriptionResponse> getCurrentSubscription(
            Long shopId
    );



    // Shop xem chi tiết yêu cầu thanh toán của mình.
    SubscriptionPaymentResponse getPayment(
            Long shopId,
            Long paymentId
    );

    // Lịch sử thanh toán của shop.
    List<SubscriptionPaymentResponse> getPaymentHistory(
            Long shopId
    );





}