package VietFreshHub.delivery.service;

import VietFreshHub.auth.service.AuthService;
import VietFreshHub.delivery.dto.AssignedDeliverySummary;
import VietFreshHub.delivery.dto.DeliveryDetailResponse;
import VietFreshHub.delivery.dto.DeliveryLocationResponse;
import VietFreshHub.delivery.entity.Delivery;
import VietFreshHub.delivery.entity.DeliveryAssignment;
import VietFreshHub.delivery.entity.DeliveryAssignmentStatus;
import VietFreshHub.delivery.repository.DeliveryAssignmentRepository;
import VietFreshHub.order.entity.Order;
import VietFreshHub.order.entity.OrderAddress;
import VietFreshHub.order.entity.ShopOrder;
import VietFreshHub.order.service.OrderService;
import VietFreshHub.shop.entity.ShopLocation;
import VietFreshHub.shop.service.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DeliveryStaffQueryService {

    private final DeliveryAssignmentRepository deliveryAssignmentRepository;
    private final AuthService authService;
    private final OrderService orderService;
    private final ShopService shopService;

    @Transactional(readOnly = true)
    public List<AssignedDeliverySummary> getAssignedDeliveries(Authentication authentication) {
        Long deliveryStaffId = authService.getCurrentDeliveryStaffId(authentication);
        return deliveryAssignmentRepository
                .findByDeliveryStaff_UserIdAndStatusInOrderByAssignedAtAscAssignmentIdAsc(
                        deliveryStaffId,
                        List.of(DeliveryAssignmentStatus.ASSIGNED, DeliveryAssignmentStatus.ACCEPTED))
                .stream()
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
                        List.of(DeliveryAssignmentStatus.ASSIGNED, DeliveryAssignmentStatus.ACCEPTED))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy phân công giao hàng."));

        return toDeliveryDetailResponse(assignment);
    }

    private AssignedDeliverySummary toAssignedDeliverySummary(DeliveryAssignment assignment) {
        Delivery delivery = assignment.getDelivery();
        ShopOrder shopOrder = delivery.getShopOrder();

        return new AssignedDeliverySummary(
                assignment.getAssignmentId(),
                delivery.getDeliveryId(),
                shopOrder.getShopOrderId(),
                shopOrder.getOrder().getOrderCode(),
                assignment.getStatus(),
                delivery.getStatus(),
                assignment.getAssignedAt(),
                assignment.getAcceptedAt()
        );
    }

    private DeliveryDetailResponse toDeliveryDetailResponse(DeliveryAssignment assignment) {
        Delivery delivery = assignment.getDelivery();
        ShopOrder shopOrder = delivery.getShopOrder();
        Order order = shopOrder.getOrder();
        OrderAddress address = orderService.getOrderAddress(order.getOrderId());
        DeliveryLocationResponse pickupLocation = shopService.findLocationByShopId(shopOrder.getShopId())
                .map(this::toDeliveryLocation)
                .orElse(null);

        return new DeliveryDetailResponse(
                assignment.getAssignmentId(),
                delivery.getDeliveryId(),
                shopOrder.getShopOrderId(),
                shopOrder.getShopId(),
                order.getOrderCode(),
                assignment.getStatus(),
                delivery.getStatus(),
                order.getPlacedAt(),
                assignment.getAssignedAt(),
                assignment.getAcceptedAt(),
                delivery.getPickedUpAt(),
                delivery.getDeliveredAt(),
                delivery.getRedeliveryRequestedAt(),
                delivery.getRedeliveryScheduledAt(),
                address == null ? null : address.getRecipientName(),
                address == null ? null : address.getPhone(),
                pickupLocation,
                toDeliveryLocation(address)
        );
    }

    private DeliveryLocationResponse toDeliveryLocation(OrderAddress address) {
        if (address == null) {
            return null;
        }

        return new DeliveryLocationResponse(
                null,
                address.getProvince(),
                null,
                address.getDistrict(),
                null,
                address.getWard(),
                address.getAddressLine(),
                null,
                null,
                null
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
