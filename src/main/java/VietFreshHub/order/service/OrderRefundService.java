package VietFreshHub.order.service;

import VietFreshHub.auth.service.CurrentCustomerService;
import VietFreshHub.exception.ResourceNotFoundException;
import VietFreshHub.order.entity.Order;
import VietFreshHub.order.repository.OrderRepository;
import VietFreshHub.order.repository.ShopOrderRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
public class OrderRefundService {

    private final CurrentCustomerService currentCustomer;
    private final OrderRepository orders;
    private final ShopOrderRepository shopOrders;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public RefundOrder getForCustomer(Long orderId, Authentication authentication) {
        return describe(findOwnedOrder(orderId, authentication));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public RefundOrder lockForCustomer(Long orderId, Authentication authentication) {
        Order order = findOwnedOrder(orderId, authentication);
        entityManager.refresh(order, LockModeType.PESSIMISTIC_WRITE);
        if (!order.getCustomer().getUserId().equals(currentCustomer.requireCustomerId(authentication))) {
            throw notFound();
        }
        return describe(order);
    }

    private Order findOwnedOrder(Long orderId, Authentication authentication) {
        Long customerId = currentCustomer.requireCustomerId(authentication);
        if (orderId == null || orderId < 1) {
            throw notFound();
        }
        return orders.findByOrderIdAndCustomerUserId(orderId, customerId).orElseThrow(this::notFound);
    }

    private RefundOrder describe(Order order) {
        List<Long> shopOrderIds = new ArrayList<>();
        for (var shopOrder : shopOrders.findByOrderOrderIdOrderByShopOrderIdAsc(order.getOrderId())) {
            shopOrderIds.add(shopOrder.getShopOrderId());
        }
        return new RefundOrder(order, List.copyOf(shopOrderIds));
    }

    private ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Không tìm thấy đơn hàng của bạn.");
    }

    public record RefundOrder(Order order, List<Long> shopOrderIds) {}
}
