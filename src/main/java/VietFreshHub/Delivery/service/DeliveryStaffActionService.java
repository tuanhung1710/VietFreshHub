package VietFreshHub.Delivery.service;

import VietFreshHub.Auth.service.AuthService;
import VietFreshHub.Delivery.entity.Delivery;
import VietFreshHub.Delivery.entity.DeliveryAssignment;
import VietFreshHub.Delivery.entity.DeliveryAssignmentStatus;
import VietFreshHub.Delivery.entity.DeliveryStatus;
import VietFreshHub.Delivery.repository.DeliveryAssignmentRepository;
import VietFreshHub.Order.entity.ShopOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DeliveryStaffActionService {

    private final DeliveryAssignmentRepository deliveryAssignmentRepository;
    private final DeliveryService deliveryService;
    private final DeliveryAssignmentService deliveryAssignmentService;
    private final DeliveryStatusHistoryWriter deliveryStatusHistoryWriter;
    private final AuthService authService;

    @Transactional
    public void confirmPickup(Long assignmentId, Authentication authentication) {
        Long deliveryStaffId = authService.getCurrentDeliveryStaffId(authentication);
        DeliveryAssignment assignment = deliveryAssignmentRepository
                .findForUpdateByAssignmentIdAndDeliveryStaff_UserId(assignmentId, deliveryStaffId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy phân công giao hàng."));
        if (assignment.getStatus() != DeliveryAssignmentStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Trạng thái phân công giao hàng hiện tại không cho phép thao tác này.");
        }

        Delivery delivery = assignment.getDelivery();
        deliveryService.markPickedUp(delivery.getDeliveryId());
        deliveryStatusHistoryWriter.record(delivery, deliveryStaffId, null);
    }

    @Transactional
    public void startDelivery(Long assignmentId, Authentication authentication) {
        Long deliveryStaffId = authService.getCurrentDeliveryStaffId(authentication);
        DeliveryAssignment assignment = deliveryAssignmentRepository
                .findForUpdateByAssignmentIdAndDeliveryStaff_UserId(assignmentId, deliveryStaffId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy phân công giao hàng."));
        if (assignment.getStatus() != DeliveryAssignmentStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Trạng thái phân công giao hàng hiện tại không cho phép thao tác này.");
        }

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
        shopOrder.markOutForDelivery();
        deliveryStatusHistoryWriter.record(delivery, deliveryStaffId, null);
    }

    @Transactional
    public void markDelivered(Long assignmentId, Authentication authentication) {
        Long deliveryStaffId = authService.getCurrentDeliveryStaffId(authentication);
        DeliveryAssignment assignment = deliveryAssignmentRepository
                .findForUpdateByAssignmentIdAndDeliveryStaff_UserId(assignmentId, deliveryStaffId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy phân công giao hàng."));
        if (assignment.getStatus() != DeliveryAssignmentStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Trạng thái phân công giao hàng hiện tại không cho phép thao tác này.");
        }

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
        shopOrder.markCompleted();
        deliveryStatusHistoryWriter.record(delivery, deliveryStaffId, null);
        deliveryAssignmentService.complete(assignmentId, authentication);
    }

    @Transactional
    public void scheduleRedelivery(Long assignmentId, LocalDateTime redeliveryScheduledAt,
                                   Authentication authentication) {
        Long deliveryStaffId = authService.getCurrentDeliveryStaffId(authentication);
        DeliveryAssignment assignment = deliveryAssignmentRepository
                .findForUpdateByAssignmentIdAndDeliveryStaff_UserId(assignmentId, deliveryStaffId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy phân công giao hàng."));
        if (assignment.getStatus() != DeliveryAssignmentStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Trạng thái phân công giao hàng hiện tại không cho phép thao tác này.");
        }

        Delivery delivery = assignment.getDelivery();
        if (delivery.getStatus() != DeliveryStatus.OUT_FOR_DELIVERY) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Trạng thái giao hàng hiện tại không cho phép thao tác này.");
        }
        ShopOrder shopOrder = delivery.getShopOrder();
        if (!"OUT_FOR_DELIVERY".equals(shopOrder.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Chỉ có thể hẹn giao lại cho đơn hàng đang giao hàng.");
        }

        deliveryService.scheduleRedelivery(delivery.getDeliveryId(), redeliveryScheduledAt);
        deliveryStatusHistoryWriter.record(delivery, deliveryStaffId, null);
        deliveryAssignmentService.complete(assignmentId, authentication);
    }
}
