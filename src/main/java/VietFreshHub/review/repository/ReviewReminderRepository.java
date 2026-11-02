package VietFreshHub.review.repository;

import VietFreshHub.order.entity.Order;
import VietFreshHub.order.entity.OrderItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ReviewReminderRepository extends Repository<Order, Long> {
    @Query("""
            select o.orderId from Order o
            where o.status = VietFreshHub.order.entity.Order.Status.COMPLETED
              and o.completedAt <= :cutoff
              and exists (select s.shopOrderId from ShopOrder s join VietFreshHub.delivery.entity.Delivery d
                  on d.shopOrder = s where s.order = o and d.status = VietFreshHub.delivery.entity.DeliveryStatus.DELIVERED)
              and not exists (select s.shopOrderId from ShopOrder s left join VietFreshHub.delivery.entity.Delivery d
                  on d.shopOrder = s where s.order = o
                    and (d.deliveryId is null or d.status <> VietFreshHub.delivery.entity.DeliveryStatus.DELIVERED))
              and exists (select i.orderItemId from OrderItem i where i.shopOrder.order = o
                  and not exists (select r.reviewId from VietFreshHub.review.entity.Review r
                      where r.orderItem = i and r.user = o.customer))
              and not exists (select n.notificationId from VietFreshHub.notification.entity.Notification n
                  where n.user = o.customer and n.type = 'REVIEW_REMINDER'
                    and n.entityType = 'CUSTOMER_ORDER' and n.entityId = o.orderId)
            order by o.completedAt, o.orderId
            """)
    List<Long> findEligibleOrderIds(@Param("cutoff") LocalDateTime cutoff, Pageable pageable);

    @Query("""
            select i from OrderItem i
            where i.shopOrder.order.orderId = :orderId
              and not exists (select r.reviewId from VietFreshHub.review.entity.Review r
                  where r.orderItem = i and r.user.userId = :userId)
            order by i.orderItemId
            """)
    List<OrderItem> findUnreviewedItemsForOrder(@Param("orderId") Long orderId, @Param("userId") Long userId);
}
