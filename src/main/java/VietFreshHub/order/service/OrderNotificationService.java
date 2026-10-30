package VietFreshHub.order.service;

import VietFreshHub.auth.service.AuthService;
import VietFreshHub.notification.dto.NotificationRow;
import VietFreshHub.notification.service.NotificationService;
import VietFreshHub.order.entity.Order;
import VietFreshHub.order.entity.ShopOrder;
import VietFreshHub.order.repository.OrderRepository;
import VietFreshHub.order.repository.ShopOrderRepository;
import VietFreshHub.payment.service.PaymentService;
import VietFreshHub.shop.service.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class OrderNotificationService {

    private final OrderRepository orderRepository;
    private final ShopOrderRepository shopOrderRepository;
    private final NotificationService notificationService;
    private final PaymentService paymentService;
    private final ShopService shopService;
    private final AuthService authService;

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onOrderPlaced(OrderPlacedEvent event) {
        Order order = orderRepository.findById(event.orderId()).orElseThrow(this::unavailable);
        if (order.getStatus() == Order.Status.PENDING && paymentService.isCodOrder(order.getOrderId())) {
            notifyActionableOrder(order, false);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyPaidOrder(Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(this::unavailable);
        if (order.getStatus() == Order.Status.PENDING && "PAID".equals(order.getPaymentStatus())) {
            notifyActionableOrder(order, true);
        }
    }

    private void notifyActionableOrder(Order order, boolean paidOnline) {
        String paymentMessage = paidOnline ? "Đã thanh toán qua VNPAY." : "Thanh toán khi nhận hàng (COD).";
        notificationService.createInApp(order.getCustomer().getUserId(), "ORDER_PLACED",
                paidOnline ? "Thanh toán đơn hàng thành công" : "Đặt hàng thành công",
                "Đơn đặt hàng #" + order.getOrderCode() + " đã được ghi nhận. " + paymentMessage,
                "ORDER", order.getOrderId());
        for (ShopOrder shopOrder : shopOrderRepository.findForOrderNotification(order.getOrderId())) {
            if (!"PENDING".equals(shopOrder.getStatus())
                    || (paidOnline && !"PAID".equals(shopOrder.getPaymentStatus()))) {
                continue;
            }
            for (Long managerId : shopService.findActiveManagerUserIds(shopOrder.getShopId())) {
                notificationService.createInApp(managerId, "NEW_SHOP_ORDER", "Bạn có đơn hàng mới",
                        "Đơn hàng #" + shopOrder.getShopOrderCode() + " đang chờ cửa hàng xác nhận. "
                                + paymentMessage, "SHOP_ORDER", shopOrder.getShopOrderId());
            }
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyConfirmed(ShopOrder shopOrder) {
        notifyCustomer(shopOrder, "SHOP_ORDER_CONFIRMED", "Cửa hàng đã xác nhận đơn hàng",
                " đã được cửa hàng xác nhận.");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyPreparing(ShopOrder shopOrder) {
        notifyCustomer(shopOrder, "SHOP_ORDER_PREPARING", "Cửa hàng đang chuẩn bị đơn hàng",
                " đang được cửa hàng chuẩn bị.");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyReady(ShopOrder shopOrder) {
        notifyCustomer(shopOrder, "SHOP_ORDER_READY", "Đơn hàng đã sẵn sàng giao",
                " đã sẵn sàng để nhân viên giao hàng đến lấy.");
    }

    private void notifyCustomer(ShopOrder shopOrder, String type, String title, String message) {
        notificationService.createInApp(shopOrder.getOrder().getCustomer().getUserId(), type, title,
                "Đơn hàng #" + shopOrder.getShopOrderCode() + message, "SHOP_ORDER", shopOrder.getShopOrderId());
    }

    @Transactional(readOnly = true)
    public String getDestination(NotificationRow notification, Authentication authentication) {
        Long userId = authService.getCurrentUserId(authentication);
        if (!notification.hasDetail()) {
            throw unavailable();
        }
        if ("ORDER".equals(notification.entityType()) && "ORDER_PLACED".equals(notification.type())
                && hasRole(authentication, "ROLE_CUSTOMER")) {
            Order order = orderRepository.findByOrderIdAndCustomerUserId(notification.entityId(), userId)
                    .orElseThrow(this::unavailable);
            return "/customer/orders/" + order.getOrderId();
        }
        if ("SHOP_ORDER".equals(notification.entityType())) {
            if ("NEW_SHOP_ORDER".equals(notification.type()) && hasRole(authentication, "ROLE_STORE_MANAGER")) {
                Long shopId = shopService.getManagedShopId(authentication);
                ShopOrder shopOrder = shopOrderRepository.findByShopOrderIdAndShopId(notification.entityId(), shopId)
                        .orElseThrow(this::unavailable);
                return "/seller/orders/" + shopOrder.getShopOrderId();
            }
            if (!"NEW_SHOP_ORDER".equals(notification.type()) && hasRole(authentication, "ROLE_CUSTOMER")) {
                ShopOrder shopOrder = shopOrderRepository
                        .findByShopOrderIdAndOrderCustomerUserId(notification.entityId(), userId)
                        .orElseThrow(this::unavailable);
                return "/customer/orders/shops/" + shopOrder.getShopOrderId();
            }
        }
        throw unavailable();
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream().anyMatch(authority -> role.equals(authority.getAuthority()));
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Chi tiết thông báo không còn khả dụng hoặc bạn không có quyền xem.");
    }
}
