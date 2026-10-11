package VietFreshHub.Delivery.service;

import VietFreshHub.Delivery.entity.Delivery;
import VietFreshHub.Delivery.entity.DeliveryStatusHistory;
import VietFreshHub.Delivery.repository.DeliveryStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class DeliveryStatusHistoryWriter {

    private final DeliveryStatusHistoryRepository deliveryStatusHistoryRepository;

    @Transactional
    public DeliveryStatusHistory record(Delivery delivery, Long changedBy, String note) {
        if (delivery == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Thông tin giao hàng không hợp lệ.");
        }
        if (delivery.getDeliveryId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Thông tin giao hàng phải được lưu trước khi ghi lịch sử.");
        }

        return deliveryStatusHistoryRepository.save(new DeliveryStatusHistory(delivery, changedBy, note));
    }
}
