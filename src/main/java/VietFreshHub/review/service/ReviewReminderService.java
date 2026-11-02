package VietFreshHub.review.service;

import VietFreshHub.notification.service.NotificationService;
import VietFreshHub.order.entity.Order;
import VietFreshHub.order.repository.OrderRepository;
import VietFreshHub.review.repository.ReviewReminderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewReminderService {
    private static final String TYPE = "REVIEW_REMINDER";
    private static final String ENTITY_TYPE = "CUSTOMER_ORDER";

    private final OrderRepository orders;
    private final ReviewReminderRepository reminders;
    private final NotificationService notifications;

    @Scheduled(fixedDelayString = "${app.review-reminder.delay-ms:60000}",
            initialDelayString = "${app.review-reminder.initial-delay-ms:30000}")
    @Transactional
    public void sendDueReminders() {
        LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC).withNano(0).minusHours(24);
        for (Long orderId : reminders.findEligibleOrderIds(cutoff, PageRequest.of(0, 100))) {
            Order order = orders.findByIdForUpdate(orderId).orElse(null);
            if (order == null || order.getStatus() != Order.Status.COMPLETED
                    || order.getCompletedAt() == null || order.getCompletedAt().isAfter(cutoff)) {
                continue;
            }

            Long customerId = order.getCustomer().getUserId();
            var unreviewedItems = reminders.findUnreviewedItemsForOrder(orderId, customerId);
            if (unreviewedItems.isEmpty()) {
                continue;
            }
            String products = unreviewedItems.stream().map(item -> item.getProductNameSnapshot())
                    .distinct().collect(Collectors.joining(", "));
            if (products.length() > 1800) {
                products = products.substring(0, 1797) + "...";
            }
            notifications.createInApp(customerId, TYPE, "Đánh giá sản phẩm bạn đã mua",
                    "Bạn chưa đánh giá các sản phẩm: " + products,
                    ENTITY_TYPE, orderId);
        }
    }
}
