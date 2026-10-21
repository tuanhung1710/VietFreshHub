package VietFreshHub.delivery.service;

import VietFreshHub.auth.service.AuthService;
import VietFreshHub.delivery.dto.DeliveryTrackingStaff;
import VietFreshHub.delivery.entity.Delivery;
import VietFreshHub.delivery.entity.DeliveryAssignment;
import VietFreshHub.delivery.entity.DeliveryAssignmentStatus;
import VietFreshHub.delivery.entity.DeliveryStatus;
import VietFreshHub.delivery.repository.DeliveryAssignmentRepository;
import VietFreshHub.delivery.repository.DeliveryRepository;
import VietFreshHub.delivery.repository.DeliveryStatusHistoryRepository;
import VietFreshHub.order.service.OrderService;
import VietFreshHub.order.service.OrderLifecycleLock;
import VietFreshHub.shop.service.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
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

    public static final int OFFER_TIMEOUT_SECONDS = 30;

    private final DeliveryAssignmentRepository deliveryAssignmentRepository;
    private final DeliveryRepository deliveryRepository;
    private final DeliveryService deliveryService;
    private final DeliveryStatusHistoryWriter deliveryStatusHistoryWriter;
    private final DeliveryStatusHistoryRepository deliveryStatusHistoryRepository;
    private final AuthService authService;
    private final ShopService shopService;
    private final OrderService orderService;
    private final ApplicationEventPublisher events;
    private final DeliveryNotificationService deliveryNotificationService;
    private final OrderLifecycleLock lifecycleLock;

    @Transactional(readOnly = true)
    public List<DeliveryTrackingStaff> getTrackingStaff(Long customerId, Long orderId) {
        return deliveryAssignmentRepository.findAcceptedAssignmentsForCustomerOrder(customerId, orderId).stream()
                .map(assignment -> new DeliveryTrackingStaff(assignment.getDelivery().getDeliveryId(),
                        assignment.getDeliveryStaff().getUserId(), assignment.getDeliveryStaff().getFullName()))
                .toList();
    }

    @Transactional
    public void accept(Long assignmentId, Authentication authentication) {
        Long deliveryStaffId = authService.getCurrentDeliveryStaffId(authentication);
        lockOrderForAssignment(assignmentId, deliveryStaffId);
        if (!authService.lockActiveDeliveryStaffIdsForAssignment().contains(deliveryStaffId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản không còn đủ điều kiện nhận đơn.");
        }
        DeliveryAssignment assignment = getAssignmentForUpdate(assignmentId, deliveryStaffId);
        requireStatus(assignment, DeliveryAssignmentStatus.ASSIGNED);

        if (deliveryAssignmentRepository.countByDeliveryStaff_UserIdAndStatusIn(deliveryStaffId,
                List.of(DeliveryAssignmentStatus.ASSIGNED, DeliveryAssignmentStatus.ACCEPTED))
                > AutoAssignmentService.MAX_ACTIVE_ASSIGNMENTS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Số phân công đang giữ vượt quá sức chứa cho phép.");
        }

        Delivery delivery = assignment.getDelivery();
        deliveryService.accept(delivery.getDeliveryId());
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (isOfferExpired(assignment, now)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Phân công giao hàng đã hết thời hạn chấp nhận.");
        }
        assignment.accept(now.isBefore(assignment.getAssignedAt())
                ? assignment.getAssignedAt() : now.withNano(0));
        deliveryStatusHistoryWriter.record(delivery, deliveryStaffId, null);
    }

    @Transactional
    public void reject(Long assignmentId, Authentication authentication) {
        Long deliveryStaffId = authService.getCurrentDeliveryStaffId(authentication);
        DeliveryAssignment assignment = getAssignmentForUpdate(assignmentId, deliveryStaffId);
        requireStatus(assignment, DeliveryAssignmentStatus.ASSIGNED);

        Delivery delivery = assignment.getDelivery();
        deliveryService.releaseAssignment(delivery.getDeliveryId());
        if (isOfferExpired(assignment, LocalDateTime.now(ZoneOffset.UTC))) {
            assignment.cancel();
        } else {
            assignment.reject();
        }
        deliveryStatusHistoryWriter.record(delivery, deliveryStaffId, null);
        events.publishEvent(new DeliveryAssignmentRetryEvent());
    }

    @Transactional(readOnly = true)
    public List<Long> findTimedOutInitialAssignmentIds() {
        return deliveryAssignmentRepository.findTimedOutAssignmentIds(
                DeliveryAssignmentStatus.ASSIGNED, DeliveryStatus.ASSIGNED,
                LocalDateTime.now(ZoneOffset.UTC).minusSeconds(OFFER_TIMEOUT_SECONDS));
    }

    @Transactional
    public void timeoutInitialAssignment(Long assignmentId) {
        Long deliveryId = deliveryAssignmentRepository.findDeliveryIdByAssignmentId(assignmentId).orElse(null);
        if (deliveryId == null) {
            return;
        }
        Delivery delivery = getDeliveryForUpdate(deliveryId);
        DeliveryAssignment assignment = deliveryAssignmentRepository.findForUpdateByAssignmentId(assignmentId)
                .orElse(null);
        if (assignment == null || assignment.getStatus() != DeliveryAssignmentStatus.ASSIGNED
                || delivery.getStatus() != DeliveryStatus.ASSIGNED
                || delivery.getRedeliveryRequestedAt() != null || delivery.getRedeliveryScheduledAt() != null
                || !isOfferExpired(assignment, LocalDateTime.now(ZoneOffset.UTC))) {
            return;
        }

        assignment.cancel();
        deliveryService.releaseAssignment(deliveryId);
        deliveryStatusHistoryWriter.record(delivery, null, null);
        events.publishEvent(new DeliveryAssignmentRetryEvent());
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
        lifecycleLock.lockForDelivery(deliveryId);
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
        deliveryNotificationService.notifyGoodsReturnConfirmed(assignment);
        events.publishEvent(new DeliveryAssignmentRetryEvent());
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
        lifecycleLock.lockForDelivery(deliveryId);
        return deliveryRepository.findForUpdateByDeliveryId(deliveryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy thông tin giao hàng."));
    }

    private boolean isOfferExpired(DeliveryAssignment assignment, LocalDateTime now) {
        return assignment.getAssignedAt() == null
                || !now.isBefore(assignment.getAssignedAt().plusSeconds(OFFER_TIMEOUT_SECONDS));
    }

    private void lockOrderForAssignment(Long assignmentId, Long staffId) {
        Long orderId = deliveryAssignmentRepository.findOrderIdByAssignmentIdAndStaffId(assignmentId, staffId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phân công giao hàng."));
        lifecycleLock.lock(orderId);
    }

    private void requireStatus(DeliveryAssignment assignment, DeliveryAssignmentStatus requiredStatus) {
        if (assignment.getStatus() != requiredStatus) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Trạng thái phân công giao hàng hiện tại không cho phép thao tác này.");
        }
    }
}
