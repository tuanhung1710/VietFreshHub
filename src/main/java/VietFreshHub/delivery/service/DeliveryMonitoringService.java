package VietFreshHub.delivery.service;

import VietFreshHub.auth.service.AuthService;
import VietFreshHub.delivery.dto.AdminDeliveryMonitoringDetail;
import VietFreshHub.delivery.dto.DeliveryMonitoringAssignment;
import VietFreshHub.delivery.dto.DeliveryMonitoringDetail;
import VietFreshHub.delivery.dto.DeliveryMonitoringHistoryEntry;
import VietFreshHub.delivery.dto.DeliveryMonitoringRow;
import VietFreshHub.delivery.entity.Delivery;
import VietFreshHub.delivery.entity.DeliveryAssignment;
import VietFreshHub.delivery.entity.DeliveryAssignmentStatus;
import VietFreshHub.delivery.entity.DeliveryStatus;
import VietFreshHub.delivery.entity.DeliveryStatusHistory;
import VietFreshHub.delivery.repository.DeliveryAssignmentRepository;
import VietFreshHub.delivery.repository.DeliveryRepository;
import VietFreshHub.delivery.repository.DeliveryStatusHistoryRepository;
import VietFreshHub.order.entity.OrderAddress;
import VietFreshHub.order.entity.ShopOrder;
import VietFreshHub.order.service.OrderLabels;
import VietFreshHub.order.service.OrderService;
import VietFreshHub.shop.entity.ShopLocation;
import VietFreshHub.shop.dto.ShopOption;
import VietFreshHub.shop.service.ShopService;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeliveryMonitoringService {

    private static final int PAGE_SIZE = 15;

    private final DeliveryRepository deliveryRepository;
    private final DeliveryAssignmentRepository deliveryAssignmentRepository;
    private final DeliveryStatusHistoryRepository deliveryStatusHistoryRepository;
    private final ShopService shopService;
    private final AuthService authService;
    private final OrderService orderService;

    @PreAuthorize("hasRole('CUSTOMER')")
    public boolean areAllFailedAndReturned(List<Long> shopOrderIds) {
        if (shopOrderIds == null || shopOrderIds.isEmpty()) {
            return false;
        }
        List<Delivery> deliveries = deliveryRepository
                .findByShopOrder_ShopOrderIdInOrderByDeliveryIdAsc(shopOrderIds);
        if (deliveries.size() != shopOrderIds.size()) {
            return false;
        }
        List<Long> deliveryIds = deliveries.stream().map(Delivery::getDeliveryId).toList();
        Map<Long, List<DeliveryAssignment>> assignments = findAssignments(deliveryIds);
        Set<String> confirmations = confirmationKeys(deliveryStatusHistoryRepository
                .findByDelivery_DeliveryIdInAndStatusAndNoteStartingWithAndChangedByIsNotNull(
                        deliveryIds, DeliveryStatus.FAILED, DeliveryStatusHistoryWriter.GOODS_RETURN_NOTE_PREFIX));
        for (Delivery delivery : deliveries) {
            DeliveryAssignment assignment = selectAssignment(delivery,
                    assignments.getOrDefault(delivery.getDeliveryId(), List.of()));
            if (!isReturnConfirmed(delivery, assignment, confirmations)) {
                return false;
            }
        }
        return true;
    }

    private boolean isReturnConfirmed(Delivery delivery, DeliveryAssignment assignment, Set<String> confirmations) {
        return delivery.getStatus() == DeliveryStatus.FAILED && assignment != null
                && assignment.getStatus() == DeliveryAssignmentStatus.COMPLETED
                && confirmations.contains(confirmationKey(delivery.getDeliveryId(),
                        DeliveryStatusHistoryWriter.goodsReturnConfirmationNote(assignment.getAssignmentId())));
    }

    public Page<DeliveryMonitoringRow> getManagedShopDeliveries(Authentication authentication,
                                                               DeliveryStatus status, String orderCode,
                                                               String direction, int page) {
        Long shopId = shopService.getManagedShopId(authentication);
        return findDeliveries(shopId, status, orderCode, direction, page);
    }

    public DeliveryMonitoringDetail getManagedShopDeliveryDetail(Long deliveryId, Authentication authentication) {
        Long shopId = shopService.getManagedShopId(authentication);
        Delivery delivery = deliveryRepository.findByDeliveryIdAndShopOrder_ShopId(deliveryId, shopId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy thông tin giao hàng."));
        return toMonitoringDetail(delivery);
    }

    public Page<DeliveryMonitoringRow> getAdminDeliveries(Authentication authentication, DeliveryStatus status,
                                                         String orderCode, Long shopId, String direction, int page) {
        requireAdmin(authentication);
        return findDeliveries(shopId, status, orderCode, direction, page);
    }

    public List<ShopOption> getAdminShopOptions(Authentication authentication) {
        requireAdmin(authentication);
        return shopService.getAdminShopOptions(authentication);
    }

    public AdminDeliveryMonitoringDetail getAdminDeliveryDetail(Long deliveryId, Authentication authentication) {
        requireAdmin(authentication);
        Delivery delivery = deliveryRepository.findByDeliveryId(deliveryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy thông tin giao hàng."));
        ShopOrder shopOrder = delivery.getShopOrder();
        var orderHistory = orderService.getProcessingHistoryForAdmin(shopOrder.getShopOrderId(), authentication);
        OrderAddress recipient = orderService.getOrderAddress(shopOrder.getOrder().getOrderId());
        ShopLocation pickup = shopService.findLocationByShopId(shopOrder.getShopId()).orElse(null);
        return new AdminDeliveryMonitoringDetail(toMonitoringDetail(delivery),
                shopOrder.getShop() == null ? null : shopOrder.getShop().getPhone(),
                pickup == null ? null : formatAddress(pickup.getFormattedAddress(), pickup.getAddressLine(),
                        pickup.getWard(), pickup.getDistrict(), pickup.getProvince()),
                recipient == null ? null : recipient.getRecipientName(),
                recipient == null ? null : recipient.getPhone(),
                recipient == null ? null : formatAddress(recipient.getFormattedAddress(), recipient.getAddressLine(),
                        recipient.getWard(), recipient.getDistrict(), recipient.getProvince()), orderHistory);
    }

    private String formatAddress(String formattedAddress, String addressLine, String ward,
                                 String district, String province) {
        if (formattedAddress != null && !formattedAddress.isBlank()) {
            return formattedAddress;
        }
        List<String> parts = new ArrayList<>();
        for (String part : new String[]{addressLine, ward, district, province}) {
            if (part != null && !part.isBlank()) {
                parts.add(part);
            }
        }
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private void requireAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getAuthorities().stream()
                .noneMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()))) {
            throw new AccessDeniedException("Bạn không có quyền giám sát giao hàng toàn hệ thống.");
        }
    }

    private Page<DeliveryMonitoringRow> findDeliveries(Long shopId, DeliveryStatus status, String orderCode,
                                                       String direction, int page) {
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số trang không hợp lệ.");
        }
        if (!"asc".equals(direction) && !"desc".equals(direction)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chiều sắp xếp không hợp lệ.");
        }
        String search = orderCode == null ? "" : orderCode.strip().toLowerCase(Locale.ROOT);
        String searchPattern = "%" + search.replace("\\", "\\\\").replace("%", "\\%")
                .replace("_", "\\_").replace("[", "\\[") + "%";
        Specification<Delivery> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (shopId != null) {
                predicates.add(builder.equal(root.get("shopOrder").get("shopId"), shopId));
            }
            if (status == DeliveryStatus.UNASSIGNED || status == DeliveryStatus.ASSIGNED
                    || status == DeliveryStatus.ACCEPTED) {
                var acceptedAssignment = query.subquery(Long.class);
                var assignmentRoot = acceptedAssignment.from(DeliveryAssignment.class);
                var fullName = assignmentRoot.get("deliveryStaff").<String>get("fullName");
                acceptedAssignment.select(assignmentRoot.get("assignmentId")).where(
                        builder.equal(assignmentRoot.get("delivery").get("deliveryId"), root.get("deliveryId")),
                        assignmentRoot.get("status").in(DeliveryAssignmentStatus.ACCEPTED, DeliveryAssignmentStatus.COMPLETED),
                        builder.isNotNull(assignmentRoot.get("acceptedAt")),
                        builder.isNotNull(fullName), builder.notEqual(builder.trim(fullName), ""));
                Predicate acceptedWithName = builder.and(
                        builder.equal(root.get("status"), DeliveryStatus.ACCEPTED),
                        builder.exists(acceptedAssignment));
                if (status == DeliveryStatus.ACCEPTED) {
                    predicates.add(acceptedWithName);
                } else {
                    predicates.add(builder.or(
                            root.get("status").in(DeliveryStatus.UNASSIGNED, DeliveryStatus.ASSIGNED),
                            builder.and(builder.equal(root.get("status"), DeliveryStatus.ACCEPTED),
                                    builder.not(builder.exists(acceptedAssignment)))));
                }
            } else if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (!search.isEmpty()) {
                Predicate matchingOrderCode = builder.like(
                        builder.lower(root.get("shopOrder").get("order").get("orderCode")), searchPattern, '\\');
                var matchingStaff = query.subquery(Long.class);
                var assignment = matchingStaff.from(DeliveryAssignment.class);
                matchingStaff.select(assignment.get("assignmentId")).where(
                        builder.equal(assignment.get("delivery").get("deliveryId"), root.get("deliveryId")),
                        assignment.get("status").in(DeliveryAssignmentStatus.ACCEPTED, DeliveryAssignmentStatus.COMPLETED),
                        builder.isNotNull(assignment.get("acceptedAt")),
                        builder.like(builder.lower(assignment.get("deliveryStaff").get("fullName")), searchPattern, '\\'));
                predicates.add(builder.or(matchingOrderCode, builder.and(
                        builder.not(root.get("status").in(DeliveryStatus.UNASSIGNED, DeliveryStatus.ASSIGNED)),
                        builder.exists(matchingStaff))));
            }
            if (query != null && query.getResultType() != Long.class && query.getResultType() != long.class) {
                Expression<?> timestamp = root.get("deliveredAt");
                Expression<Integer> nullRank = builder.<Integer>selectCase()
                        .when(builder.isNull(timestamp), 1).otherwise(0);
                boolean ascending = "asc".equals(direction);
                query.orderBy(builder.asc(nullRank),
                        ascending ? builder.asc(timestamp) : builder.desc(timestamp),
                        ascending ? builder.asc(root.get("deliveryId")) : builder.desc(root.get("deliveryId")));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        Page<Delivery> deliveries = deliveryRepository.findAll(specification, PageRequest.of(page, PAGE_SIZE));
        List<Long> deliveryIds = deliveries.getContent().stream().map(Delivery::getDeliveryId).toList();
        Map<Long, List<DeliveryAssignment>> assignments = findAssignments(deliveryIds);
        Set<String> confirmations = deliveryIds.isEmpty() ? Set.of() : confirmationKeys(deliveryStatusHistoryRepository
                .findByDelivery_DeliveryIdInAndStatusAndNoteStartingWithAndChangedByIsNotNull(
                        deliveryIds, DeliveryStatus.FAILED, DeliveryStatusHistoryWriter.GOODS_RETURN_NOTE_PREFIX));
        return deliveries.map(delivery -> toMonitoringRow(delivery,
                assignments.getOrDefault(delivery.getDeliveryId(), List.of()), confirmations, true));
    }

    private Map<Long, List<DeliveryAssignment>> findAssignments(List<Long> deliveryIds) {
        Map<Long, List<DeliveryAssignment>> assignments = new HashMap<>();
        if (!deliveryIds.isEmpty()) {
            for (DeliveryAssignment assignment : deliveryAssignmentRepository
                    .findByDelivery_DeliveryIdInOrderByAssignedAtDescAssignmentIdDesc(deliveryIds)) {
                assignments.computeIfAbsent(assignment.getDelivery().getDeliveryId(), key -> new ArrayList<>())
                        .add(assignment);
            }
        }
        return assignments;
    }

    private DeliveryAssignment selectAssignment(Delivery delivery, List<DeliveryAssignment> assignments) {
        if (delivery.getStatus() == DeliveryStatus.UNASSIGNED) {
            return null;
        }
        if (delivery.getStatus() == DeliveryStatus.DELIVERED) {
            return assignments.stream().filter(assignment -> assignment.getStatus() == DeliveryAssignmentStatus.COMPLETED)
                    .findFirst().orElse(null);
        }
        if (delivery.getStatus() == DeliveryStatus.FAILED) {
            DeliveryAssignment responsible = assignments.stream()
                    .filter(assignment -> assignment.getStatus() == DeliveryAssignmentStatus.ACCEPTED)
                    .findFirst().orElse(null);
            return responsible != null ? responsible : assignments.stream()
                    .filter(assignment -> assignment.getStatus() == DeliveryAssignmentStatus.COMPLETED)
                    .findFirst().orElse(null);
        }
        DeliveryAssignment current = assignments.stream().filter(this::isActive).findFirst().orElse(null);
        if (current != null) {
            return current;
        }
        return assignments.isEmpty() ? null : assignments.getFirst();
    }

    private DeliveryMonitoringRow toMonitoringRow(Delivery delivery, List<DeliveryAssignment> assignments,
                                                   Set<String> confirmations, boolean listView) {
        ShopOrder shopOrder = delivery.getShopOrder();
        DeliveryAssignment assignment = selectAssignment(delivery, assignments);
        boolean current = assignment != null && isActive(assignment);
        String assignmentLabel = assignment == null ? "Không có dữ liệu phân công phù hợp trong lịch sử."
                : assignmentStatusLabel(assignment.getStatus());
        String staffName = assignment == null ? "Thiếu dữ liệu nhân viên giao hàng trong lịch sử"
                : staffName(assignment);
        if (delivery.getStatus() == DeliveryStatus.UNASSIGNED) {
            assignmentLabel = "Chưa phân công";
            staffName = "Chưa phân công";
        } else if (delivery.getStatus() == DeliveryStatus.DELIVERED && assignment != null) {
            assignmentLabel = "Phân công đã hoàn tất giao hàng thành công";
        }
        String deliveryLabel = OrderLabels.delivery(delivery.getStatus().name());
        if (listView) {
            String fullName = assignment == null ? null : assignment.getDeliveryStaff().getFullName();
            boolean acceptedStaff = assignment != null && assignment.getAcceptedAt() != null
                    && (assignment.getStatus() == DeliveryAssignmentStatus.ACCEPTED
                    || assignment.getStatus() == DeliveryAssignmentStatus.COMPLETED)
                    && fullName != null && !fullName.isBlank();
            staffName = acceptedStaff ? fullName.strip() : null;
            if (delivery.getStatus() == DeliveryStatus.UNASSIGNED || delivery.getStatus() == DeliveryStatus.ASSIGNED
                    || delivery.getStatus() == DeliveryStatus.ACCEPTED) {
                boolean assigned = delivery.getStatus() == DeliveryStatus.ACCEPTED && acceptedStaff;
                deliveryLabel = assigned ? "Đã phân công" : "Chờ phân công giao hàng";
                if (!assigned) {
                    staffName = null;
                }
            }
        }

        boolean returnPending = delivery.getStatus() == DeliveryStatus.FAILED
                && assignment != null && assignment.getStatus() == DeliveryAssignmentStatus.ACCEPTED
                && delivery.getPickedUpAt() != null && delivery.getDeliveredAt() == null
                && delivery.getFailureReason() != null && !delivery.getFailureReason().isBlank()
                && !isLegacyRedelivery(delivery);
        boolean returnConfirmed = isReturnConfirmed(delivery, assignment, confirmations);
        String returnLabel = null;
        if (delivery.getStatus() == DeliveryStatus.FAILED) {
            returnLabel = returnConfirmed ? "Cửa hàng đã xác nhận nhận lại hàng"
                    : returnPending ? "Đang chờ trả hàng về cửa hàng"
                    : "Chưa có xác nhận trả hàng phù hợp trong lịch sử; cần đối soát thủ công";
        }

        return new DeliveryMonitoringRow(
                delivery.getDeliveryId(), shopOrder.getOrder().getOrderCode(), shopOrder.getShopOrderId(),
                shopOrder.getShopId(), shopOrder.getShop() == null ? null : shopOrder.getShop().getShopName(),
                deliveryLabel,
                current || delivery.getStatus() == DeliveryStatus.UNASSIGNED
                        ? "Phân công hiện tại" : "Phân công gần nhất trong lịch sử",
                assignment == null ? null : assignment.getAssignmentId(), assignmentLabel, staffName,
                assignment == null ? null : OrderLabels.local(assignment.getAssignedAt()),
                assignment == null ? null : OrderLabels.local(assignment.getAcceptedAt()),
                delivery.getStatus() == DeliveryStatus.DELIVERED ? OrderLabels.local(delivery.getDeliveredAt()) : null,
                OrderLabels.local(delivery.getUpdatedAt()), returnLabel, returnPending);
    }

    private DeliveryMonitoringDetail toMonitoringDetail(Delivery delivery) {
        List<DeliveryAssignment> assignments = findAssignments(List.of(delivery.getDeliveryId()))
                .getOrDefault(delivery.getDeliveryId(), List.of());
        List<DeliveryStatusHistory> events = deliveryStatusHistoryRepository
                .findByDelivery_DeliveryIdOrderByCreatedAtAscHistoryIdAsc(delivery.getDeliveryId());
        List<DeliveryStatusHistory> visibleEvents = events.stream()
                .filter(event -> event.getStatus() != DeliveryStatus.UNASSIGNED
                        && event.getStatus() != DeliveryStatus.ASSIGNED)
                .toList();
        List<Long> performerIds = visibleEvents.stream().map(DeliveryStatusHistory::getChangedBy)
                .filter(userId -> userId != null).distinct().toList();
        Map<Long, String> performerNames = authService.getUserFullNames(performerIds);
        List<DeliveryMonitoringHistoryEntry> history = visibleEvents.stream().map(event -> {
            boolean returnConfirmation = event.getStatus() == DeliveryStatus.FAILED && event.getChangedBy() != null
                    && assignments.stream().anyMatch(assignment -> assignment.getStatus() == DeliveryAssignmentStatus.COMPLETED
                    && DeliveryStatusHistoryWriter.goodsReturnConfirmationNote(assignment.getAssignmentId())
                    .equals(event.getNote()));
            String performerName = event.getChangedBy() == null ? "Hệ thống"
                    : performerNames.getOrDefault(event.getChangedBy(), "Chưa ghi nhận họ tên");
            return new DeliveryMonitoringHistoryEntry(historyStatusLabel(event.getStatus()),
                    OrderLabels.local(event.getCreatedAt()), performerName,
                    returnConfirmation ? "Cửa hàng đã xác nhận nhận lại hàng" : event.getNote());
        }).toList();
        List<DeliveryMonitoringAssignment> assignmentHistory = assignments.stream().map(assignment ->
                new DeliveryMonitoringAssignment(assignment.getAssignmentId(),
                        assignmentStatusLabel(assignment.getStatus()), staffName(assignment),
                        OrderLabels.local(assignment.getAssignedAt()), OrderLabels.local(assignment.getAcceptedAt()),
                        isActive(assignment))).toList();
        return new DeliveryMonitoringDetail(
                toMonitoringRow(delivery, assignments, confirmationKeys(events), false),
                OrderLabels.local(delivery.getCreatedAt()), OrderLabels.local(delivery.getAssignedAt()),
                OrderLabels.local(delivery.getPickedUpAt()),
                delivery.getStatus() == DeliveryStatus.DELIVERED ? OrderLabels.local(delivery.getDeliveredAt()) : null,
                delivery.getFailureReason(), isLegacyRedelivery(delivery), assignmentHistory, history);
    }

    private String historyStatusLabel(DeliveryStatus status) {
        return switch (status) {
            case ACCEPTED -> "Nhân viên giao hàng đã nhận nhiệm vụ";
            case PICKED_UP -> "Nhân viên đã lấy hàng";
            case OUT_FOR_DELIVERY -> "Đang giao hàng";
            default -> OrderLabels.delivery(status.name());
        };
    }

    private Set<String> confirmationKeys(List<DeliveryStatusHistory> events) {
        Set<String> keys = new HashSet<>();
        for (DeliveryStatusHistory event : events) {
            if (event.getStatus() == DeliveryStatus.FAILED && event.getChangedBy() != null && event.getNote() != null
                    && event.getNote().startsWith(DeliveryStatusHistoryWriter.GOODS_RETURN_NOTE_PREFIX)) {
                keys.add(confirmationKey(event.getDelivery().getDeliveryId(), event.getNote()));
            }
        }
        return keys;
    }

    private String confirmationKey(Long deliveryId, String note) {
        return deliveryId + ":" + note;
    }

    private String staffName(DeliveryAssignment assignment) {
        String name = assignment.getDeliveryStaff().getFullName();
        return name == null || name.isBlank()
                ? "Nhân viên #" + assignment.getDeliveryStaff().getUserId() + " (chưa có tên trong hồ sơ)" : name;
    }

    private boolean isActive(DeliveryAssignment assignment) {
        return assignment.getStatus() == DeliveryAssignmentStatus.ASSIGNED
                || assignment.getStatus() == DeliveryAssignmentStatus.ACCEPTED;
    }

    private boolean isLegacyRedelivery(Delivery delivery) {
        return delivery.getStatus() == DeliveryStatus.REDELIVERY_SCHEDULED
                || delivery.getRedeliveryRequestedAt() != null || delivery.getRedeliveryScheduledAt() != null;
    }

    private String assignmentStatusLabel(DeliveryAssignmentStatus status) {
        return switch (status) {
            case ASSIGNED -> "Đang chờ shipper chấp nhận";
            case ACCEPTED -> "Shipper đã nhận đơn";
            case REJECTED -> "Đã từ chối phân công";
            case CANCELLED -> "Phân công đã hết hạn hoặc bị hủy";
            case COMPLETED -> "Phân công đã hoàn tất";
        };
    }
}
