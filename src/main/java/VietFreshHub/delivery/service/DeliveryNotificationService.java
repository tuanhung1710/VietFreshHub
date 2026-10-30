package VietFreshHub.delivery.service;

import VietFreshHub.auth.service.AuthService;
import VietFreshHub.delivery.entity.Delivery;
import VietFreshHub.delivery.entity.DeliveryAssignment;
import VietFreshHub.delivery.entity.DeliveryAssignmentStatus;
import VietFreshHub.delivery.repository.DeliveryAssignmentRepository;
import VietFreshHub.delivery.repository.DeliveryRepository;
import VietFreshHub.notification.dto.NotificationRow;
import VietFreshHub.notification.service.NotificationService;
import VietFreshHub.order.entity.Order;
import VietFreshHub.shop.service.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DeliveryNotificationService {

    private final NotificationService notificationService;
    private final ShopService shopService;
    private final AuthService authService;
    private final DeliveryRepository deliveryRepository;
    private final DeliveryAssignmentRepository deliveryAssignmentRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyStarted(Delivery delivery) {
        Order order = delivery.getShopOrder().getOrder();
        notificationService.createInApp(order.getCustomer().getUserId(), "DELIVERY_STARTED",
                "Đơn hàng đang được giao",
                deliveryLabel(delivery) + " đã bắt đầu được giao đến bạn.", "DELIVERY", delivery.getDeliveryId());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyCompleted(Delivery delivery) {
        Order order = delivery.getShopOrder().getOrder();
        notificationService.createInApp(order.getCustomer().getUserId(), "DELIVERY_COMPLETED",
                "Giao hàng thành công", deliveryLabel(delivery) + " đã được giao thành công.",
                "DELIVERY", delivery.getDeliveryId());
        notifyManagers(delivery, "DELIVERY_COMPLETED", "Đơn hàng đã giao thành công",
                "Shipper đã hoàn thành " + deliveryLabel(delivery) + ".");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyFailed(Delivery delivery) {
        Order order = delivery.getShopOrder().getOrder();
        notificationService.createInApp(order.getCustomer().getUserId(), "DELIVERY_FAILED",
                "Giao hàng không thành công",
                deliveryLabel(delivery) + " chưa được giao thành công. Vui lòng xem chi tiết đơn hàng.",
                "DELIVERY", delivery.getDeliveryId());
        notifyManagers(delivery, "DELIVERY_FAILED", "Đơn giao hàng thất bại",
                deliveryLabel(delivery) + " giao thất bại. Vui lòng theo dõi việc hoàn trả hàng về cửa hàng.");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyGoodsReturnConfirmed(DeliveryAssignment assignment) {
        String orderCode = assignment.getDelivery().getShopOrder().getShopOrderCode();
        notificationService.createInApp(assignment.getDeliveryStaff().getUserId(), "GOODS_RETURN_CONFIRMED",
                "Cửa hàng đã xác nhận nhận lại hàng",
                "Cửa hàng đã xác nhận nhận lại hàng của đơn #" + orderCode + ".",
                "DELIVERY_ASSIGNMENT", assignment.getAssignmentId());
    }

    @Transactional(readOnly = true)
    public String getDestination(NotificationRow notification, Authentication authentication) {
        Long userId = authService.getCurrentUserId(authentication);
        if (!notification.hasDetail()) {
            throw unavailable();
        }
        if ("DELIVERY_ASSIGNMENT".equals(notification.entityType())) {
            authService.getCurrentDeliveryStaffId(authentication);
            DeliveryAssignment assignment = deliveryAssignmentRepository
                    .findByAssignmentIdAndDeliveryStaff_UserIdAndStatusIn(notification.entityId(), userId,
                            List.of(DeliveryAssignmentStatus.ASSIGNED, DeliveryAssignmentStatus.ACCEPTED,
                                    DeliveryAssignmentStatus.COMPLETED, DeliveryAssignmentStatus.REJECTED,
                                    DeliveryAssignmentStatus.CANCELLED))
                    .orElseThrow(this::unavailable);
            if (assignment.getStatus() == DeliveryAssignmentStatus.REJECTED
                    || assignment.getStatus() == DeliveryAssignmentStatus.CANCELLED) {
                return "/delivery";
            }
            return "/delivery/assignments/" + assignment.getAssignmentId();
        }
        Delivery delivery = deliveryRepository.findByDeliveryId(notification.entityId())
                .orElseThrow(this::unavailable);
        Order order = delivery.getShopOrder().getOrder();
        if (hasRole(authentication, "ROLE_CUSTOMER") && order.getCustomer().getUserId().equals(userId)) {
            return "/customer/orders/shops/" + delivery.getShopOrder().getShopOrderId() + "/tracking";
        }
        if (!"DELIVERY_STARTED".equals(notification.type()) && hasRole(authentication, "ROLE_STORE_MANAGER")) {
            Long shopId = shopService.getManagedShopId(authentication);
            if (shopId.equals(delivery.getShopOrder().getShopId())) {
                return "/seller/deliveries/" + delivery.getDeliveryId();
            }
        }
        throw unavailable();
    }

    private void notifyManagers(Delivery delivery, String type, String title, String content) {
        for (Long userId : shopService.findActiveManagerUserIds(delivery.getShopOrder().getShopId())) {
            notificationService.createInApp(userId, type, title, content, "DELIVERY", delivery.getDeliveryId());
        }
    }

    private String deliveryLabel(Delivery delivery) {
        return "Đơn giao hàng #" + delivery.getDeliveryId() + " thuộc mã #"
                + delivery.getShopOrder().getShopOrderCode();
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream().anyMatch(authority -> role.equals(authority.getAuthority()));
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Chi tiết thông báo không còn khả dụng hoặc bạn không có quyền xem.");
    }
}
