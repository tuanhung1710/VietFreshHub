package VietFreshHub.Delivery.service;

import VietFreshHub.Delivery.entity.Delivery;
import VietFreshHub.Delivery.entity.DeliveryStatus;
import VietFreshHub.Delivery.repository.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;

    @Transactional
    public void markAssigned(Long deliveryId) {
        Delivery delivery = getDeliveryForUpdate(deliveryId);
        requireStatus(delivery, DeliveryStatus.UNASSIGNED);
        delivery.markAssigned(utcNow());
    }

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
        delivery.startDelivery();
    }

    @Transactional
    public void scheduleRedelivery(Long deliveryId, LocalDateTime scheduledAt) {
        Delivery delivery = getDeliveryForUpdate(deliveryId);
        requireStatus(delivery, DeliveryStatus.OUT_FOR_DELIVERY);
        LocalDateTime requestedAt = utcNow();

        if (scheduledAt == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Vui lòng chọn thời gian giao lại.");
        }
        if (!scheduledAt.isAfter(requestedAt)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Thời gian giao lại phải sau thời gian yêu cầu.");
        }
        if (!scheduledAt.toLocalDate().equals(requestedAt.toLocalDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Thời gian giao lại phải trong cùng ngày yêu cầu.");
        }
        if (scheduledAt.toLocalTime().isAfter(LocalTime.of(23, 0))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Thời gian giao lại không được sau 23:00:00.");
        }

        delivery.scheduleRedelivery(requestedAt, scheduledAt);
    }

    @Transactional
    public void resumeRedelivery(Long deliveryId) {
        Delivery delivery = getDeliveryForUpdate(deliveryId);
        requireStatus(delivery, DeliveryStatus.REDELIVERY_SCHEDULED);
        delivery.resumeRedelivery();
    }

    @Transactional
    public void markDelivered(Long deliveryId) {
        Delivery delivery = getDeliveryForUpdate(deliveryId);
        requireStatus(delivery, DeliveryStatus.OUT_FOR_DELIVERY);
        delivery.markDelivered(utcNow());
    }

    private Delivery getDeliveryForUpdate(Long deliveryId) {
        return deliveryRepository.findForUpdateByDeliveryId(deliveryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy thông tin giao hàng."));
    }

    private void requireStatus(Delivery delivery, DeliveryStatus requiredStatus) {
        if (delivery.getStatus() != requiredStatus) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Trạng thái giao hàng hiện tại không cho phép thao tác này.");
        }
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC).withNano(0);
    }
}
