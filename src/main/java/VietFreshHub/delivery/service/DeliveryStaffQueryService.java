package VietFreshHub.delivery.service;

import VietFreshHub.auth.service.AuthService;
import VietFreshHub.delivery.dto.AssignedDeliverySummary;
import VietFreshHub.delivery.dto.AvailableDeliveryResponse;
import VietFreshHub.delivery.dto.DeliveryDetailResponse;
import VietFreshHub.delivery.dto.DeliveryLocationResponse;
import VietFreshHub.delivery.dto.DailyCodSummary;
import VietFreshHub.delivery.dto.DeliveryHistoryFilter;
import VietFreshHub.delivery.dto.DeliveryHistoryPage;
import VietFreshHub.delivery.dto.DeliveryHistoryRow;
import VietFreshHub.delivery.entity.Delivery;
import VietFreshHub.delivery.entity.DeliveryAssignment;
import VietFreshHub.delivery.entity.DeliveryAssignmentStatus;
import VietFreshHub.delivery.entity.DeliveryStatus;
import VietFreshHub.delivery.repository.DeliveryAssignmentRepository;
import VietFreshHub.delivery.repository.DeliveryRepository;
import VietFreshHub.delivery.repository.DeliveryStatusHistoryRepository;
import VietFreshHub.delivery.repository.DeliveryStaffHistoryRepository;
import VietFreshHub.order.entity.Order;
import VietFreshHub.order.entity.OrderAddress;
import VietFreshHub.order.entity.ShopOrder;
import VietFreshHub.order.service.OrderService;
import VietFreshHub.order.service.OrderLabels;
import VietFreshHub.payment.service.PaymentService;
import VietFreshHub.shop.entity.ShopLocation;
import VietFreshHub.shop.service.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DeliveryStaffQueryService {

    private static final ZoneId DELIVERY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final DeliveryAssignmentRepository deliveryAssignmentRepository;
    private final DeliveryRepository deliveryRepository;
    private final DeliveryStatusHistoryRepository deliveryStatusHistoryRepository;
    private final AuthService authService;
    private final OrderService orderService;
    private final ShopService shopService;
    private final PaymentService paymentService;
    private final DeliveryStaffHistoryRepository staffHistory;

    @Transactional(readOnly = true)
    public List<AvailableDeliveryResponse> getAvailableDeliveries(Authentication authentication) {
        authService.getCurrentDeliveryStaffId(authentication);
        return deliveryRepository.findAll(DeliveryRepository.availableForClaim(), Sort.by("createdAt", "deliveryId")).stream()
                .map(this::toAvailableDeliveryResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DeliveryDetailResponse getDeliveryDetailForClaim(Long deliveryId, Authentication authentication) {
        authService.getCurrentDeliveryStaffId(authentication);
        Delivery delivery = deliveryRepository.findOne(DeliveryRepository.availableForClaim()
                        .and((root, query, builder) -> builder.equal(root.get("deliveryId"), deliveryId)))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Đơn giao hàng không còn trong danh sách có thể nhận."));
        return toDeliveryDetailResponse(delivery, null);
    }

    @Transactional(readOnly = true)
    public long getActiveAssignmentCount(Authentication authentication) {
        Long deliveryStaffId = authService.getCurrentDeliveryStaffId(authentication);
        return deliveryAssignmentRepository.countByDeliveryStaff_UserIdAndStatusIn(deliveryStaffId,
                List.of(DeliveryAssignmentStatus.ASSIGNED, DeliveryAssignmentStatus.ACCEPTED));
    }

    private AvailableDeliveryResponse toAvailableDeliveryResponse(Delivery delivery) {
        ShopOrder shopOrder = delivery.getShopOrder();
        OrderAddress address = orderService.getOrderAddress(shopOrder.getOrder().getOrderId());
        DeliveryLocationResponse pickupLocation = shopService.findLocationByShopId(shopOrder.getShopId())
                .map(this::toDeliveryLocation)
                .orElse(null);
        return new AvailableDeliveryResponse(delivery.getDeliveryId(), shopOrder.getShopOrderCode(),
                shopOrder.getShop() == null ? null : shopOrder.getShop().getShopName(),
                pickupLocation, toDeliveryLocation(address));
    }

    @Transactional(readOnly = true)
    public DailyCodSummary getDailyCodSummary(Authentication authentication, LocalDate date) {
        Long staffId = authService.getCurrentDeliveryStaffId(authentication);
        LocalDate selectedDate = date == null ? LocalDate.now(DELIVERY_ZONE) : date;
        if (selectedDate.getYear() < 1 || selectedDate.getYear() > 9999) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ngày nằm ngoài phạm vi dữ liệu được hỗ trợ.");
        }
        LocalDateTime end = selectedDate.equals(LocalDate.of(9999, 12, 31))
                ? null : historyDayStart(selectedDate.plusDays(1));
        return staffHistory.dailyCod(staffId, selectedDate, historyDayStart(selectedDate), end);
    }

    @Transactional(readOnly = true)
    public DeliveryHistoryPage getDeliveryHistory(Authentication authentication, DeliveryHistoryFilter filter, int page) {
        Long staffId = authService.getCurrentDeliveryStaffId(authentication);
        var shops = staffHistory.shops(staffId);
        var methods = staffHistory.paymentMethods(staffId);
        validateHistoryFilter(filter, page, shops, methods);
        LocalDate today = LocalDate.now(DELIVERY_ZONE);
        LocalDate from = null;
        LocalDate to = null;
        switch (filter.getPeriod()) {
            case "TODAY" -> {
                from = today;
                to = today;
            }
            case "LAST_7" -> {
                from = today.minusDays(6);
                to = today;
            }
            case "LAST_30" -> {
                from = today.minusDays(29);
                to = today;
            }
            case "CUSTOM" -> {
                from = filter.getFromDate();
                to = filter.getToDate();
            }
            default -> {}
        }
        LocalDateTime start = from == null ? null : historyDayStart(from);
        LocalDateTime until = to == null || to.equals(LocalDate.of(9999, 12, 31))
                ? null : historyDayStart(to.plusDays(1));
        var records = staffHistory.findHistory(staffId, filter, start, until, PageRequest.of(page, 15));
        if (page > 0 && records.getPageable().getOffset() >= records.getTotalElements()) {
            throw historyFilterError("Trang lịch sử không tồn tại. Vui lòng quay về trang đầu.");
        }
        return new DeliveryHistoryPage(records, shops, methods, staffHistory.countAll(staffId),
                staffHistory.countUnresolved(staffId));
    }

    @Transactional(readOnly = true)
    public DeliveryHistoryPage getDeliveryHistoryOptions(Authentication authentication) {
        Long staffId = authService.getCurrentDeliveryStaffId(authentication);
        return new DeliveryHistoryPage(Page.empty(PageRequest.of(0, 15)), staffHistory.shops(staffId),
                staffHistory.paymentMethods(staffId), staffHistory.countAll(staffId), staffHistory.countUnresolved(staffId));
    }

    @Transactional(readOnly = true)
    public DeliveryHistoryRow getHistoricalDeliveryDetail(Long assignmentId, Authentication authentication) {
        Long staffId = authService.getCurrentDeliveryStaffId(authentication);
        return staffHistory.findDetail(staffId, assignmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lịch sử giao hàng."));
    }

    private void validateHistoryFilter(DeliveryHistoryFilter filter, int page,
                                       List<DeliveryHistoryPage.Option> shops, List<DeliveryHistoryPage.Option> methods) {
        filter.setKeyword(filter.getKeyword() == null ? "" : filter.getKeyword().strip());
        filter.setStatus(historyValue(filter.getStatus(), "ALL"));
        filter.setPeriod(historyValue(filter.getPeriod(), "ALL"));
        filter.setReturnStatus(historyValue(filter.getReturnStatus(), "ALL"));
        filter.setSort(historyValue(filter.getSort(), "NEWEST"));
        filter.setPaymentMethod(historyValue(filter.getPaymentMethod(), ""));
        if (page < 0 || (long) page * 15 > Integer.MAX_VALUE) {
            throw historyFilterError("Số trang không hợp lệ.");
        }
        if (!List.of("ALL", "DELIVERED", "FAILED").contains(filter.getStatus())) {
            throw historyFilterError("Kết quả giao hàng không hợp lệ.");
        }
        if (!List.of("ALL", "TODAY", "LAST_7", "LAST_30", "CUSTOM").contains(filter.getPeriod())) {
            throw historyFilterError("Khoảng thời gian không hợp lệ.");
        }
        if (!List.of("ALL", "PENDING", "CONFIRMED").contains(filter.getReturnStatus())) {
            throw historyFilterError("Điều kiện trả hàng không hợp lệ.");
        }
        if (!List.of("NEWEST", "OLDEST", "AMOUNT_HIGH", "AMOUNT_LOW").contains(filter.getSort())) {
            throw historyFilterError("Lựa chọn sắp xếp không hợp lệ.");
        }
        if (filter.getShopId() != null && shops.stream()
                .noneMatch(option -> option.value().equals(filter.getShopId().toString()))) {
            throw historyFilterError("Cửa hàng không thuộc lịch sử giao hàng của bạn.");
        }
        if (!filter.getPaymentMethod().isEmpty() && methods.stream()
                .noneMatch(option -> option.value().equals(filter.getPaymentMethod()))) {
            throw historyFilterError("Phương thức thanh toán không có trong lịch sử giao hàng của bạn.");
        }
        if ((filter.getMinAmount() != null && filter.getMinAmount().signum() < 0)
                || (filter.getMaxAmount() != null && filter.getMaxAmount().signum() < 0)) {
            throw historyFilterError("Giá trị đơn hàng không được âm.");
        }
        if (filter.getMinAmount() != null && filter.getMaxAmount() != null
                && filter.getMinAmount().compareTo(filter.getMaxAmount()) > 0) {
            throw historyFilterError("Giá trị từ không được lớn hơn giá trị đến.");
        }
        if ("CUSTOM".equals(filter.getPeriod())) {
            for (LocalDate date : new LocalDate[]{filter.getFromDate(), filter.getToDate()}) {
                if (date != null && (date.getYear() < 1 || date.getYear() > 9999)) {
                    throw historyFilterError("Ngày nằm ngoài phạm vi dữ liệu được hỗ trợ.");
                }
            }
            LocalDate today = LocalDate.now(DELIVERY_ZONE);
            if (filter.getFromDate() != null && filter.getFromDate().isAfter(today)) {
                throw historyFilterError("Từ ngày không được lớn hơn ngày hiện tại.");
            }
            if (filter.getToDate() != null && filter.getToDate().isAfter(today)) {
                throw historyFilterError("Đến ngày không được lớn hơn ngày hiện tại.");
            }
            if (filter.getFromDate() != null && filter.getToDate() != null
                    && filter.getFromDate().isAfter(filter.getToDate())) {
                throw historyFilterError("Từ ngày không được sau Đến ngày.");
            }
        }
    }

    private String historyValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    private LocalDateTime historyDayStart(LocalDate date) {
        LocalDateTime utc = date.atStartOfDay(DELIVERY_ZONE).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        return utc.getYear() < 1 ? LocalDateTime.of(1, 1, 1, 0, 0) : utc;
    }

    private ResponseStatusException historyFilterError(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    @Transactional(readOnly = true)
    public List<AssignedDeliverySummary> getAssignedDeliveries(Authentication authentication) {
        Long deliveryStaffId = authService.getCurrentDeliveryStaffId(authentication);
        return deliveryAssignmentRepository
                .findByDeliveryStaff_UserIdAndStatusInOrderByAssignedAtAscAssignmentIdAsc(
                        deliveryStaffId,
                        List.of(DeliveryAssignmentStatus.ASSIGNED, DeliveryAssignmentStatus.ACCEPTED))
                .stream()
                .filter(assignment -> isSupportedDelivery(assignment.getDelivery()))
                .map(this::toAssignedDeliverySummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public DeliveryDetailResponse getAssignedDeliveryDetail(Long assignmentId, Authentication authentication) {
        Long deliveryStaffId = authService.getCurrentDeliveryStaffId(authentication);
        DeliveryAssignment assignment = deliveryAssignmentRepository
                .findByAssignmentIdAndDeliveryStaff_UserIdAndStatusIn(
                        assignmentId,
                        deliveryStaffId,
                        List.of(DeliveryAssignmentStatus.ASSIGNED, DeliveryAssignmentStatus.ACCEPTED,
                                DeliveryAssignmentStatus.COMPLETED))
                .filter(    candidate -> isSupportedDelivery(candidate.getDelivery()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy phân công giao hàng."));

        return toDeliveryDetailResponse(assignment.getDelivery(), assignment);
    }

    private AssignedDeliverySummary toAssignedDeliverySummary(DeliveryAssignment assignment) {
        Delivery delivery = assignment.getDelivery();
        ShopOrder shopOrder = delivery.getShopOrder();

        return new AssignedDeliverySummary(
                assignment.getAssignmentId(),
                delivery.getDeliveryId(),
                shopOrder.getShopOrderId(),
                shopOrder.getShopOrderCode(),
                assignment.getStatus(),
                delivery.getStatus(),
                toDeliveryLocalTime(assignment.getAssignedAt()),
                toDeliveryLocalTime(assignment.getAcceptedAt()),
                assignmentStatusLabel(assignment.getStatus()),
                OrderLabels.delivery(delivery.getStatus().name())
        );
    }

    private DeliveryDetailResponse toDeliveryDetailResponse(Delivery delivery, DeliveryAssignment assignment) {
        ShopOrder shopOrder = delivery.getShopOrder();
        Order order = shopOrder.getOrder();
        OrderAddress address = orderService.getOrderAddress(order.getOrderId());
        DeliveryLocationResponse pickupLocation = shopService.findLocationByShopId(shopOrder.getShopId())
                .map(this::toDeliveryLocation)
                .orElse(null);

        return new DeliveryDetailResponse(
                assignment == null ? null : assignment.getAssignmentId(),
                delivery.getDeliveryId(),
                shopOrder.getShopOrderId(),
                shopOrder.getShopId(),
                shopOrder.getShopOrderCode(),
                assignment == null ? null : assignment.getStatus(),
                delivery.getStatus(),
                order.getPlacedAt(),
                assignment == null ? null : toDeliveryLocalTime(assignment.getAssignedAt()),
                assignment == null ? null : toDeliveryLocalTime(assignment.getAcceptedAt()),
                toDeliveryLocalTime(delivery.getPickedUpAt()),
                delivery.getStatus() == DeliveryStatus.DELIVERED ? toDeliveryLocalTime(delivery.getDeliveredAt()) : null,
                delivery.getFailureReason(),
                assignment == null ? null : returnStatusLabel(assignment),
                assignment == null ? null : assignmentStatusLabel(assignment.getStatus()),
                OrderLabels.delivery(delivery.getStatus().name()),
                address == null ? null : address.getRecipientName(),
                address == null ? null : address.getPhone(),
                pickupLocation,
                toDeliveryLocation(address),
                shopOrder.getShop() == null ? null : shopOrder.getShop().getShopName(),
                paymentService.isCodOrder(order.getOrderId()) ? shopOrder.getTotalAmount() : BigDecimal.ZERO
        );
    }

    private boolean isSupportedDelivery(Delivery delivery) {
        if (delivery.getRedeliveryRequestedAt() != null || delivery.getRedeliveryScheduledAt() != null) {
            return false;
        }
        return switch (delivery.getStatus()) {
            case UNASSIGNED, ASSIGNED, ACCEPTED, PICKED_UP, OUT_FOR_DELIVERY, DELIVERED, FAILED -> true;
            default -> false;
        };
    }

    private String returnStatusLabel(DeliveryAssignment assignment) {
        Delivery delivery = assignment.getDelivery();
        if (delivery.getStatus() != DeliveryStatus.FAILED) {
            return null;
        }
        if (assignment.getStatus() == DeliveryAssignmentStatus.ACCEPTED && delivery.getPickedUpAt() != null
                && delivery.getDeliveredAt() == null && delivery.getFailureReason() != null
                && !delivery.getFailureReason().isBlank()) {
            return "Đang chờ trả hàng về cửa hàng";
        }
        if (assignment.getStatus() == DeliveryAssignmentStatus.COMPLETED
                && deliveryStatusHistoryRepository.existsByDelivery_DeliveryIdAndStatusAndNoteAndChangedByIsNotNull(
                        delivery.getDeliveryId(), DeliveryStatus.FAILED,
                        DeliveryStatusHistoryWriter.goodsReturnConfirmationNote(assignment.getAssignmentId()))) {
            return "Cửa hàng đã xác nhận nhận lại hàng.";
        }
        return "Chưa có xác nhận trả hàng phù hợp trong lịch sử; cần đối soát thủ công.";
    }

    private String assignmentStatusLabel(DeliveryAssignmentStatus status) {
        return switch (status) {
            case ASSIGNED -> "Phân công cũ cần đối soát";
            case ACCEPTED -> "Đã chấp nhận";
            case REJECTED -> "Đã từ chối";
            case CANCELLED -> "Đã hết hạn hoặc hủy";
            case COMPLETED -> "Phân công đã hoàn tất";
        };
    }

    private LocalDateTime toDeliveryLocalTime(LocalDateTime utcTime) {
        if (utcTime == null) {
            return null;
        }

        return utcTime.atOffset(ZoneOffset.UTC).atZoneSameInstant(DELIVERY_ZONE).toLocalDateTime();
    }

    private DeliveryLocationResponse toDeliveryLocation(OrderAddress address) {
        if (address == null) {
            return null;
        }

        String formattedAddress = address.getFormattedAddress();
        if (formattedAddress == null || formattedAddress.isBlank()) {
            List<String> parts = new ArrayList<>();
            for (String part : new String[]{address.getAddressLine(), address.getWard(),
                    address.getDistrict(), address.getProvince()}) {
                if (part != null && !part.isBlank()) {
                    parts.add(part);
                }
            }
            formattedAddress = String.join(", ", parts);
        }

        return new DeliveryLocationResponse(
                address.getProvinceId(),
                address.getProvince(),
                address.getDistrictId(),
                address.getDistrict(),
                address.getWardId(),
                address.getWard(),
                address.getAddressLine(),
                formattedAddress,
                address.getLatitude(),
                address.getLongitude()
        );
    }

    private DeliveryLocationResponse toDeliveryLocation(ShopLocation location) {
        return new DeliveryLocationResponse(
                location.getProvinceId(),
                location.getProvince(),
                location.getDistrictId(),
                location.getDistrict(),
                location.getWardId(),
                location.getWard(),
                location.getAddressLine(),
                location.getFormattedAddress(),
                location.getLatitude(),
                location.getLongitude()
        );
    }
}
