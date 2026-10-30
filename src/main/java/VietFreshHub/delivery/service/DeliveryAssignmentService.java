package VietFreshHub.delivery.service;

import VietFreshHub.auth.service.AuthService;
import VietFreshHub.auth.entity.User;
import VietFreshHub.delivery.dto.DeliveryTrackingStaff;
import VietFreshHub.delivery.entity.Delivery;
import VietFreshHub.delivery.entity.DeliveryAssignment;
import VietFreshHub.delivery.entity.DeliveryAssignmentStatus;
import VietFreshHub.delivery.entity.DeliveryStatus;
import VietFreshHub.delivery.repository.DeliveryAssignmentRepository;
import VietFreshHub.delivery.repository.DeliveryRepository;
import VietFreshHub.delivery.repository.DeliveryStatusHistoryRepository;
import VietFreshHub.order.service.OrderService;
import VietFreshHub.shop.service.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DeliveryAssignmentService {

    public static final int MAX_ACTIVE_ASSIGNMENTS = 5;

    private final DeliveryAssignmentRepository deliveryAssignmentRepository;
    private final DeliveryRepository deliveryRepository;
    private final DeliveryStatusHistoryWriter deliveryStatusHistoryWriter;
    private final DeliveryStatusHistoryRepository deliveryStatusHistoryRepository;
    private final AuthService authService;
    private final ShopService shopService;
    private final OrderService orderService;
    private final DeliveryNotificationService deliveryNotificationService;

    @Transactional(readOnly = true)
    public List<DeliveryTrackingStaff> getTrackingStaff(Long customerId, Long orderId) {
        return deliveryAssignmentRepository.findAcceptedAssignmentsForCustomerOrder(customerId, orderId).stream()
                .map(assignment -> new DeliveryTrackingStaff(assignment.getDelivery().getDeliveryId(),
                        assignment.getDeliveryStaff().getUserId(), assignment.getDeliveryStaff().getFullName()))
                .toList();
    }

    @Transactional
    public Long claimDelivery(Long deliveryId, Authentication authentication) {
        User deliveryStaff = authService.lockCurrentDeliveryStaffForAssignment(authentication);
        Long deliveryStaffId = deliveryStaff.getUserId();
        if (deliveryAssignmentRepository.countByDeliveryStaff_UserIdAndStatusIn(deliveryStaffId,
                List.of(DeliveryAssignmentStatus.ASSIGNED, DeliveryAssignmentStatus.ACCEPTED))
                >= MAX_ACTIVE_ASSIGNMENTS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bạn đã nhận đủ 5 đơn giao hàng đang hoạt động.");
        }

        Delivery delivery = getDeliveryForUpdate(deliveryId);
        if (delivery.getStatus() != DeliveryStatus.UNASSIGNED
                || deliveryAssignmentRepository.existsByDelivery_DeliveryIdAndStatusIn(deliveryId,
                        List.of(DeliveryAssignmentStatus.ASSIGNED, DeliveryAssignmentStatus.ACCEPTED))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đơn giao hàng đã có người nhận.");
        }
        if (!"READY_FOR_DELIVERY".equals(delivery.getShopOrder().getStatus())
                || delivery.getRedeliveryRequestedAt() != null || delivery.getRedeliveryScheduledAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đơn giao hàng không còn đủ điều kiện nhận.");
        }

        LocalDateTime acceptedAt = LocalDateTime.now(ZoneOffset.UTC).withNano(0);
        delivery.accept(acceptedAt);
        DeliveryAssignment assignment = deliveryAssignmentRepository.save(
                new DeliveryAssignment(delivery, deliveryStaff, acceptedAt));
        deliveryStatusHistoryWriter.record(delivery, deliveryStaffId, null);
        deliveryNotificationService.notifyClaimed(assignment);
        return assignment.getAssignmentId();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public DeliveryAssignment getAcceptedAssignmentForUpdate(Long assignmentId, Authentication authentication) {
        Long deliveryStaffId = authService.getCurrentDeliveryStaffId(authentication);
        DeliveryAssignment assignment = getAssignmentForUpdate(assignmentId, deliveryStaffId);
        requireStatus(assignment, DeliveryAssignmentStatus.ACCEPTED);
        return assignment;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public DeliveryAssignment getAcceptedAssignmentForDeliveryCompletion(Long assignmentId,
                                                                       Authentication authentication) {
        Long deliveryStaffId = authService.getCurrentDeliveryStaffId(authentication);
        Long orderId = deliveryAssignmentRepository.findOrderIdByAssignmentIdAndStaffId(assignmentId, deliveryStaffId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy phân công giao hàng."));
        orderService.lockOrderForDeliveryCompletion(orderId);
        DeliveryAssignment assignment = getAssignmentForUpdate(assignmentId, deliveryStaffId);
        requireStatus(assignment, DeliveryAssignmentStatus.ACCEPTED);
        return assignment;
    }

    @Transactional
    public void confirmGoodsReturn(Long deliveryId, Authentication authentication) {
        Long shopId = shopService.getManagedShopId(authentication);
        Long managerId = authService.getCurrentUserId(authentication);
        Long orderId = deliveryRepository.findOrderIdByDeliveryIdAndShopId(deliveryId, shopId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy thông tin giao hàng."));
        orderService.lockOrderForDeliveryCompletion(orderId);
        Delivery delivery = deliveryRepository.findForUpdateByDeliveryIdAndShopOrder_ShopId(deliveryId, shopId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy thông tin giao hàng."));
        if (delivery.getStatus() != DeliveryStatus.FAILED || delivery.getPickedUpAt() == null
                || delivery.getDeliveredAt() != null
                || delivery.getFailureReason() == null || delivery.getFailureReason().isBlank()
                || delivery.getRedeliveryRequestedAt() != null || delivery.getRedeliveryScheduledAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Đơn giao hàng không thuộc trường hợp chờ xác nhận trả hàng.");
        }
        DeliveryAssignment assignment = deliveryAssignmentRepository.findForUpdateByDelivery_DeliveryIdAndStatus(
                        deliveryId, DeliveryAssignmentStatus.ACCEPTED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Phân công đã hoàn tất hoặc không còn chờ trả hàng."));
        String confirmationNote = DeliveryStatusHistoryWriter.goodsReturnConfirmationNote(assignment.getAssignmentId());
        if (deliveryStatusHistoryRepository.existsByDelivery_DeliveryIdAndStatusAndNoteAndChangedByIsNotNull(
                deliveryId, DeliveryStatus.FAILED, confirmationNote)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cửa hàng đã xác nhận nhận lại hàng.");
        }

        assignment.complete();
        deliveryStatusHistoryWriter.record(delivery, managerId, confirmationNote);
        orderService.reconcileCodPayment(orderId);
        deliveryNotificationService.notifyGoodsReturnConfirmed(assignment);
    }

    private DeliveryAssignment getAssignmentForUpdate(Long assignmentId, Long deliveryStaffId) {
        Long deliveryId = deliveryAssignmentRepository.findDeliveryIdByAssignmentIdAndStaffId(
                        assignmentId, deliveryStaffId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy phân công giao hàng."));
        Delivery delivery = getDeliveryForUpdate(deliveryId);
        if (delivery.getStatus() == DeliveryStatus.REDELIVERY_SCHEDULED
                || delivery.getRedeliveryRequestedAt() != null || delivery.getRedeliveryScheduledAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Hồ sơ giao lại cũ chỉ được xem và cần đối soát thủ công.");
        }
        return deliveryAssignmentRepository.findForUpdateByAssignmentIdAndDeliveryStaff_UserId(
                        assignmentId, deliveryStaffId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy phân công giao hàng."));
    }

    private Delivery getDeliveryForUpdate(Long deliveryId) {
        return deliveryRepository.findForUpdateByDeliveryId(deliveryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy thông tin giao hàng."));
    }

    private void requireStatus(DeliveryAssignment assignment, DeliveryAssignmentStatus requiredStatus) {
        if (assignment.getStatus() != requiredStatus) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Trạng thái phân công giao hàng hiện tại không cho phép thao tác này.");
        }
    }
}
