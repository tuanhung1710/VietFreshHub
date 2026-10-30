package VietFreshHub.order.service;

import VietFreshHub.auth.service.CurrentCustomerService;
import VietFreshHub.exception.ResourceNotFoundException;
import VietFreshHub.order.entity.Order;
import VietFreshHub.order.entity.ShopOrder;
import VietFreshHub.order.repository.OrderRepository;
import VietFreshHub.order.repository.OrderStatusHistoryRepository;
import VietFreshHub.order.repository.ShopOrderRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
public class OrderRefundService {

    private final CurrentCustomerService currentCustomer;
    private final OrderRepository orders;
    private final ShopOrderRepository shopOrders;
    private final OrderStatusHistoryRepository orderHistory;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public Order getForCustomer(Long orderId, Authentication authentication) {
        return findOwnedOrder(orderId, authentication);
    }

    @Transactional(readOnly = true)
    public RefundShopOrder getShopForCustomer(Long shopOrderId, Authentication authentication) {
        return describeShop(findOwnedShopOrder(shopOrderId, authentication));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public RefundShopOrder lockShopForCustomer(Long shopOrderId, Authentication authentication) {
        ShopOrder shopOrder = findOwnedShopOrder(shopOrderId, authentication);
        Order order = shopOrder.getOrder();
        entityManager.refresh(order, LockModeType.PESSIMISTIC_WRITE);
        entityManager.refresh(shopOrder, LockModeType.PESSIMISTIC_WRITE);
        if (!order.getCustomer().getUserId().equals(currentCustomer.requireCustomerId(authentication))
                || !order.getOrderId().equals(shopOrder.getOrder().getOrderId())) {
            throw notFound();
        }
        return describeShop(shopOrder);
    }

    private ShopOrder findOwnedShopOrder(Long shopOrderId, Authentication authentication) {
        Long customerId = currentCustomer.requireCustomerId(authentication);
        if (shopOrderId == null || shopOrderId < 1) throw notFound();
        return shopOrders.findByShopOrderIdAndOrderCustomerUserId(shopOrderId, customerId).orElseThrow(this::notFound);
    }

    private RefundShopOrder describeShop(ShopOrder shopOrder) {
        boolean cancelled = "CANCELLED".equals(shopOrder.getStatus()) && shopOrder.getCancelledAt() != null
                && shopOrder.getConfirmedAt() == null;
        boolean cancelledByCustomer = false;
        if (cancelled) {
            for (var event : orderHistory.findByShopOrderShopOrderIdOrderByCreatedAtAscHistoryIdAsc(shopOrder.getShopOrderId())) {
                if (!"PENDING".equals(event.getStatus()) && !"CANCELLED".equals(event.getStatus())) {
                    return new RefundShopOrder(shopOrder, false);
                }
                if ("CANCELLED".equals(event.getStatus()) && event.getChangedBy() != null
                        && shopOrder.getOrder().getCustomer().getUserId().equals(event.getChangedBy().getUserId())) {
                    cancelledByCustomer = true;
                }
            }
        }
        return new RefundShopOrder(shopOrder, cancelled && cancelledByCustomer);
    }

    private Order findOwnedOrder(Long orderId, Authentication authentication) {
        Long customerId = currentCustomer.requireCustomerId(authentication);
        if (orderId == null || orderId < 1) {
            throw notFound();
        }
        return orders.findByOrderIdAndCustomerUserId(orderId, customerId).orElseThrow(this::notFound);
    }

    private ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Không tìm thấy đơn hàng của bạn.");
    }

    public record RefundShopOrder(ShopOrder shopOrder, boolean cancelledByCustomerBeforeConfirmation) {}
}
