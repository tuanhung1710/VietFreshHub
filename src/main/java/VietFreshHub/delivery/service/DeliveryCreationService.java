package VietFreshHub.delivery.service;

import VietFreshHub.delivery.entity.Delivery;
import VietFreshHub.delivery.entity.DeliveryStatus;
import VietFreshHub.delivery.repository.DeliveryRepository;
import VietFreshHub.order.entity.ShopOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class DeliveryCreationService {

    private final DeliveryRepository deliveryRepository;
    private final AutoAssignmentService autoAssignmentService;
    private final DeliveryStatusHistoryWriter deliveryStatusHistoryWriter;

    @Transactional
    public Delivery ensureDeliveryForReadyShopOrder(ShopOrder shopOrder) {
        if (shopOrder == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Đơn hàng không hợp lệ.");
        }
        if (shopOrder.getShopOrderId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Đơn hàng phải được lưu trước khi tạo thông tin giao hàng.");
        }
        if (!"READY_FOR_DELIVERY".equals(shopOrder.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Chỉ có thể tạo thông tin giao hàng cho đơn hàng đã sẵn sàng giao hàng.");
        }

        Delivery delivery = deliveryRepository.findByShopOrder_ShopOrderId(shopOrder.getShopOrderId())
                .orElse(null);
        if (delivery == null) {
            delivery = new Delivery();
            delivery.setShopOrder(shopOrder);
            delivery = deliveryRepository.save(delivery);
            deliveryStatusHistoryWriter.record(delivery, null, null);
        }

        if (delivery.getStatus() == DeliveryStatus.UNASSIGNED) {
            autoAssignmentService.autoAssign(delivery.getDeliveryId());
        }

        return delivery;
    }
}
