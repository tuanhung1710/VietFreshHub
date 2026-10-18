package VietFreshHub.subscription.services;

import VietFreshHub.subscription.dto.ShopSubscriptionResponse;

import java.util.Optional;

public interface ShopSubscriptionService {

    // Gói đăng ký hiện tại còn hiệu lực.
    Optional<ShopSubscriptionResponse> getCurrentSubscription(
            Long shopId
    );









}