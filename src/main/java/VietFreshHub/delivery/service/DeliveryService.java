package VietFreshHub.delivery.service;

import VietFreshHub.delivery.entity.Delivery;
import VietFreshHub.delivery.entity.DeliveryStatus;
import VietFreshHub.delivery.repository.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;

    @Transactional
    public void releaseAssignment(Long deliveryId) {
        Delivery delivery = getDeliveryForUpdate(deliveryId);
        requireStatus(delivery, DeliveryStatus.ASSIGNED);
        delivery.releaseAssignment();
    }

    @Transactional
    public void accept(Long deliveryId) {
        Delivery delivery = getDeliveryForUpdate(deliveryId);
        requireStatus(delivery, DeliveryStatus.ASSIGNED);
        if (!"READY_FOR_DELIVERY".equals(delivery.getShopOrder().getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đơn hàng chưa sẵn sàng giao hàng.");
        }
        delivery.accept();
    }

    @Transactional
    public void markPickedUp(Long deliveryId) {
        Delivery delivery = getDeliveryForUpdate(deliveryId);
        requireStatus(delivery, DeliveryStatus.ACCEPTED);
        delivery.markPickedUp(utcNow());
    }

    @Transactional
    public void startDelivery(Long deliveryId) {
        Delivery delivery = getDeliveryForUpdate(deliveryId);
        requireStatus(delivery, DeliveryStatus.PICKED_UP);
        requirePickedUp(delivery);
        delivery.startDelivery();
    }

    @Transactional
    public void markDelivered(Long deliveryId) {
        Delivery delivery = getDeliveryForUpdate(deliveryId);
        requireStatus(delivery, DeliveryStatus.OUT_FOR_DELIVERY);
        requirePickedUp(delivery);
        delivery.markDelivered(utcNow());
    }

    @Transactional
    public void markFailed(Long deliveryId, String failureReason) {
        Delivery delivery = getDeliveryForUpdate(deliveryId);
        requireStatus(delivery, DeliveryStatus.OUT_FOR_DELIVERY);
        requirePickedUp(delivery);
        String reason = failureReason == null ? "" : failureReason.strip();
        if (reason.isBlank() || reason.length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Vui lòng nhập lý do giao hàng thất bại, tối đa 1.000 ký tự.");
        }
        if (delivery.getDeliveredAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đơn giao hàng đã có thời gian giao thành công.");
        }
        delivery.markFailed(reason);
    }

    private Delivery getDeliveryForUpdate(Long deliveryId) {
        return deliveryRepository.findForUpdateByDeliveryId(deliveryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy thông tin giao hàng."));
    }

    private void requireStatus(Delivery delivery, DeliveryStatus requiredStatus) {
        if (delivery.getStatus() != requiredStatus
                || delivery.getRedeliveryRequestedAt() != null || delivery.getRedeliveryScheduledAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Trạng thái giao hàng hiện tại không cho phép thao tác này.");
        }
    }

    private void requirePickedUp(Delivery delivery) {
        if (delivery.getPickedUpAt() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Chưa ghi nhận thời gian nhận hàng.");
        }
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC).withNano(0);
    }
}
