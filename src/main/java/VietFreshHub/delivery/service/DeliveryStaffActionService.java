package VietFreshHub.delivery.service;

import VietFreshHub.delivery.entity.Delivery;
import VietFreshHub.delivery.entity.DeliveryAssignment;
import VietFreshHub.delivery.entity.DeliveryStatus;
import VietFreshHub.order.entity.ShopOrder;
import VietFreshHub.order.service.OrderService;
import VietFreshHub.payout.service.SellerPayoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class DeliveryStaffActionService {

    private final DeliveryService deliveryService;
    private final DeliveryAssignmentService deliveryAssignmentService;
    private final DeliveryStatusHistoryWriter deliveryStatusHistoryWriter;
    private final OrderService orderService;
    private final DeliveryNotificationService deliveryNotificationService;
    private final SellerPayoutService sellerPayoutService;
    private final ApplicationEventPublisher events;

    @Transactional
    public void confirmPickup(Long assignmentId, Authentication authentication) {
        DeliveryAssignment assignment = deliveryAssignmentService.getAcceptedAssignmentForUpdate(
                assignmentId, authentication);
        Delivery delivery = assignment.getDelivery();
        if (!"READY_FOR_DELIVERY".equals(delivery.getShopOrder().getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đơn hàng chưa sẵn sàng giao hàng.");
        }
        deliveryService.markPickedUp(delivery.getDeliveryId());
        deliveryStatusHistoryWriter.record(delivery, assignment.getDeliveryStaff().getUserId(), null);
    }

    @Transactional
    public void startDelivery(Long assignmentId, Authentication authentication) {
        DeliveryAssignment assignment = deliveryAssignmentService.getAcceptedAssignmentForUpdate(
                assignmentId, authentication);
        Delivery delivery = assignment.getDelivery();
        if (delivery.getStatus() != DeliveryStatus.PICKED_UP) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Trạng thái giao hàng hiện tại không cho phép thao tác này.");
        }
        ShopOrder shopOrder = delivery.getShopOrder();
        if (!"READY_FOR_DELIVERY".equals(shopOrder.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Chỉ có thể bắt đầu giao hàng cho đơn hàng sẵn sàng giao hàng.");
        }

        deliveryService.startDelivery(delivery.getDeliveryId());
        orderService.startShopOrderDelivery(shopOrder.getShopOrderId(), assignment.getDeliveryStaff().getUserId());
        deliveryStatusHistoryWriter.record(delivery, assignment.getDeliveryStaff().getUserId(), null);
        deliveryNotificationService.notifyStarted(delivery);
    }

    @Transactional
    public void markDelivered(Long assignmentId, Authentication authentication) {
        DeliveryAssignment assignment = deliveryAssignmentService.getAcceptedAssignmentForDeliveryCompletion(
                assignmentId, authentication);
        Delivery delivery = assignment.getDelivery();
        if (delivery.getStatus() != DeliveryStatus.OUT_FOR_DELIVERY) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Trạng thái giao hàng hiện tại không cho phép thao tác này.");
        }
        ShopOrder shopOrder = delivery.getShopOrder();
        if (!"OUT_FOR_DELIVERY".equals(shopOrder.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Chỉ có thể hoàn tất đơn hàng đang giao hàng.");
        }

        deliveryService.markDelivered(delivery.getDeliveryId());
        orderService.completeDeliveredShopOrder(shopOrder.getShopOrderId(), delivery.getDeliveredAt(),
                assignment.getDeliveryStaff().getUserId());
        sellerPayoutService.createForDeliveredOrder(shopOrder.getShopOrderId(), delivery.getDeliveredAt());
        deliveryStatusHistoryWriter.record(delivery, assignment.getDeliveryStaff().getUserId(), null);
        assignment.complete();
        deliveryNotificationService.notifyCompleted(delivery);
        events.publishEvent(new DeliveryAssignmentRetryEvent());
    }

    @Transactional
    public void markFailed(Long assignmentId, String failureReason, Authentication authentication) {
        DeliveryAssignment assignment = deliveryAssignmentService.getAcceptedAssignmentForUpdate(
                assignmentId, authentication);
        Delivery delivery = assignment.getDelivery();
        if (delivery.getStatus() != DeliveryStatus.OUT_FOR_DELIVERY) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Trạng thái giao hàng hiện tại không cho phép thao tác này.");
        }
        if (!"OUT_FOR_DELIVERY".equals(delivery.getShopOrder().getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Chỉ có thể ghi nhận thất bại cho đơn hàng đang giao hàng.");
        }

        deliveryService.markFailed(delivery.getDeliveryId(), failureReason);
        deliveryStatusHistoryWriter.record(delivery, assignment.getDeliveryStaff().getUserId(),
                "Giao hàng thất bại; đang chờ trả hàng về cửa hàng.");
        deliveryNotificationService.notifyFailed(delivery);
    }
}
