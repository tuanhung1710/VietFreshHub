package VietFreshHub.review;

import VietFreshHub.auth.entity.User;
import VietFreshHub.notification.service.NotificationService;
import VietFreshHub.order.entity.Order;
import VietFreshHub.order.entity.OrderItem;
import VietFreshHub.order.repository.OrderRepository;
import VietFreshHub.review.repository.ReviewReminderRepository;
import VietFreshHub.review.service.ReviewReminderService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReviewReminderServiceTest {
    @Test
    void sendsOneConsolidatedInAppReminderWithOnlyUnreviewedProducts() {
        OrderRepository orders = mock(OrderRepository.class);
        ReviewReminderRepository reminders = mock(ReviewReminderRepository.class);
        NotificationService notifications = mock(NotificationService.class);
        ReviewReminderService service = new ReviewReminderService(orders, reminders, notifications);
        LocalDateTime completedAt = LocalDateTime.now(ZoneOffset.UTC).withNano(0).minusHours(25);
        User customer = new User();
        customer.setUserId(42L);
        Order order = new Order();
        order.setCustomer(customer);
        order.setStatus(Order.Status.COMPLETED);
        order.setCompletedAt(completedAt);
        when(reminders.findEligibleOrderIds(any(), any())).thenReturn(List.of(9L));
        when(orders.findByIdForUpdate(9L)).thenReturn(Optional.of(order));
        OrderItem first = item("Apples");
        OrderItem second = item("Dragon fruit");
        when(reminders.findUnreviewedItemsForOrder(9L, 42L)).thenReturn(List.of(first, second));

        service.sendDueReminders();

        verify(notifications).createInApp(eq(42L), eq("REVIEW_REMINDER"), anyString(),
                contains("Apples, Dragon fruit"), eq("CUSTOMER_ORDER"), eq(9L));
        verifyNoMoreInteractions(notifications);
    }

    @Test
    void doesNotSendWhenNoEligibleUnreviewedItemsRemain() {
        OrderRepository orders = mock(OrderRepository.class);
        ReviewReminderRepository reminders = mock(ReviewReminderRepository.class);
        NotificationService notifications = mock(NotificationService.class);
        ReviewReminderService service = new ReviewReminderService(orders, reminders, notifications);
        User customer = new User();
        customer.setUserId(42L);
        Order order = new Order();
        order.setCustomer(customer);
        order.setStatus(Order.Status.COMPLETED);
        order.setCompletedAt(LocalDateTime.now(ZoneOffset.UTC).minusDays(2));
        when(reminders.findEligibleOrderIds(any(), any())).thenReturn(List.of(9L));
        when(orders.findByIdForUpdate(9L)).thenReturn(Optional.of(order));
        when(reminders.findUnreviewedItemsForOrder(9L, 42L)).thenReturn(List.of());

        service.sendDueReminders();

        verifyNoInteractions(notifications);
    }

    private OrderItem item(String productName) {
        OrderItem item = new OrderItem();
        item.setProductNameSnapshot(productName);
        return item;
    }
}
