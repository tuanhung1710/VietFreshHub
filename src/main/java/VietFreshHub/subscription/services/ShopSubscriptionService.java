package VietFreshHub.subscription.services;

import VietFreshHub.subscription.dto.ShopSubscriptionResponse;
import VietFreshHub.subscription.dto.SubscriptionPaymentForm;
import VietFreshHub.subscription.dto.SubscriptionPaymentResponse;
import VietFreshHub.subscription.entity.SubscriptionPaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ShopSubscriptionService {

    // Gói đăng ký hiện tại còn hiệu lực.
    Optional<ShopSubscriptionResponse> getCurrentSubscription(
            Long shopId
    );

    // Lịch sử đăng ký của shop.
    List<ShopSubscriptionResponse> getSubscriptionHistory(
            Long shopId
    );

    // Tạo yêu cầu thanh toán: đăng ký, gia hạn hoặc nâng cấp.
    SubscriptionPaymentResponse createPaymentRequest(
            Long shopId,
            SubscriptionPaymentForm form
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

    // Hủy gói đang có hiệu lực.
    void cancelSubscription(
            Long shopId,
            Long subscriptionId
    );

    // Admin xem danh sách đăng ký, có phân trang.
    Page<ShopSubscriptionResponse> getAllSubscriptions(
            Pageable pageable
    );

    // Admin xem yêu cầu thanh toán theo trạng thái.
    List<SubscriptionPaymentResponse> getPaymentsByStatus(
            SubscriptionPaymentStatus status
    );

    // Admin xác nhận đã nhận tiền và kích hoạt gói.
    ShopSubscriptionResponse approvePayment(
            Long paymentId,
            Long adminUserId,
            String transactionReference
    );

    // Admin đánh dấu yêu cầu thanh toán thất bại.
    void rejectPayment(
            Long paymentId,
            Long adminUserId,
            String reason
    );
}