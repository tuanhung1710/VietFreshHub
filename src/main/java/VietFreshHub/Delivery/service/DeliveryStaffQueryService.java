package VietFreshHub.Delivery.service;

import VietFreshHub.Auth.service.AuthService;
import VietFreshHub.Delivery.dto.AssignedDeliverySummary;
import VietFreshHub.Delivery.dto.DeliveryDetailResponse;
import VietFreshHub.Delivery.dto.DeliveryLocationResponse;
import VietFreshHub.Delivery.entity.Delivery;
import VietFreshHub.Delivery.entity.DeliveryAssignment;
import VietFreshHub.Delivery.entity.DeliveryAssignmentStatus;
import VietFreshHub.Delivery.repository.DeliveryAssignmentRepository;
import VietFreshHub.Order.entity.Order;
import VietFreshHub.Order.entity.OrderAddress;
import VietFreshHub.Order.entity.ShopOrder;
import VietFreshHub.Shop.entity.ShopLocation;
import VietFreshHub.Shop.service.ShopService;
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
        OrderAddress address = order.getOrderAddress();
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
                address.getProvinceId(),
                address.getProvince(),
                address.getDistrictId(),
                address.getDistrict(),
                address.getWardId(),
                address.getWard(),
                address.getAddressLine(),
                address.getFormattedAddress(),
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
