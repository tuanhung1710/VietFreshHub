package VietFreshHub.payment.service;

import VietFreshHub.delivery.entity.Delivery;
import VietFreshHub.delivery.entity.DeliveryStatus;
import VietFreshHub.order.entity.Order;
import VietFreshHub.order.entity.ShopOrder;
import VietFreshHub.order.service.OrderLifecycleLock;
import VietFreshHub.payment.entity.Payment;
import VietFreshHub.payment.repository.PaymentRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderLifecycleLock lifecycleLock;
    private final EntityManager em;

    /**
     * COD delivery success is the receipt for the full server-side ShopOrder total.
     * One unique Delivery per ShopOrder is counted once; there is no incrementing
     * cash counter to replay. Requires the delivery and child completion in this tx.
     * Current checkout creates exactly one order-level COD Payment.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean recordCodCollection(Long orderId, Long shopOrderId) {
        Order order = lifecycleLock.lock(orderId);
        require(order.getStatus() != Order.Status.CANCELLED, "Không thể thu COD cho đơn đã hủy.");
        // Persist delivery/child evidence before reading aggregates or refreshing Payments.
        em.flush();
        List<ShopOrder> children = em.createQuery("select s from ShopOrder s where s.order.orderId = :id order by s.shopOrderId", ShopOrder.class)
                .setParameter("id", orderId).getResultList();
        // A caller may have cached another shop before acquiring the parent lock.
        // Parent locking serializes writers; refresh after flush sees their commits.
        children.forEach(em::refresh);
        ShopOrder target = children.stream().filter(s -> s.getShopOrderId().equals(shopOrderId)).findFirst()
                .orElseThrow(() -> conflict("Đơn cửa hàng không thuộc đơn hàng cần thu COD."));
        List<Payment> payments = paymentRepository.findByOrderOrderIdOrderByPaymentIdAsc(orderId);
        require(payments.size() == 1, "Đơn COD phải có đúng một giao dịch thanh toán.");
        Payment payment = payments.getFirst();
        em.refresh(payment, LockModeType.PESSIMISTIC_WRITE);
        require(orderId.equals(payment.getOrder().getOrderId())
                && "COD".equals(payment.getPaymentMethod().getMethodCode()), "Phương thức thanh toán không phải COD.");
        require(payment.getStatus() == Payment.Status.PENDING || payment.getStatus() == Payment.Status.PAID,
                "Giao dịch COD không còn đủ điều kiện thu tiền.");

        BigDecimal subtotal = BigDecimal.ZERO, discount = BigDecimal.ZERO, shipping = BigDecimal.ZERO, due = BigDecimal.ZERO;
        for (ShopOrder child : children) {
            require(child.getSubtotal().signum() >= 0 && child.getDiscountTotal().signum() >= 0
                    && child.getDiscountTotal().compareTo(child.getSubtotal()) <= 0 && child.getShippingFee().signum() >= 0,
                    "Số tiền đơn cửa hàng không hợp lệ.");
            BigDecimal calculated = child.getSubtotal().subtract(child.getDiscountTotal()).add(child.getShippingFee());
            require(calculated.compareTo(child.getTotalAmount()) == 0, "Tổng tiền đơn cửa hàng không khớp.");
            subtotal = subtotal.add(child.getSubtotal()); discount = discount.add(child.getDiscountTotal());
            shipping = shipping.add(child.getShippingFee()); due = due.add(calculated);
        }
        require(subtotal.compareTo(order.getSubtotal()) == 0 && discount.compareTo(order.getDiscountTotal()) == 0
                && shipping.compareTo(order.getShippingFee()) == 0 && due.compareTo(order.getGrandTotal()) == 0
                && due.compareTo(payment.getAmount()) == 0, "Nghĩa vụ thanh toán COD không khớp tổng tiền đơn hàng.");

        List<Delivery> evidence = em.createQuery("select d from Delivery d where d.shopOrder.order.orderId = :id", Delivery.class)
                .setParameter("id", orderId).getResultList();
        evidence.forEach(em::refresh);
        var deliveries = evidence.stream()
                .collect(Collectors.toMap(d -> d.getShopOrder().getShopOrderId(), Function.identity()));
        require(isCollected(target, deliveries.get(shopOrderId)), "Chưa có bằng chứng giao thành công và thu đủ COD.");
        BigDecimal collected = BigDecimal.ZERO;
        boolean allCollected = true;
        LocalDateTime paidAt = null;
        for (ShopOrder child : children) {
            Delivery delivery = deliveries.get(child.getShopOrderId());
            if (!isCollected(child, delivery)) { allCollected = false; continue; }
            collected = collected.add(child.getTotalAmount());
            if (paidAt == null || delivery.getDeliveredAt().isAfter(paidAt)) paidAt = delivery.getDeliveredAt();
        }
        if (!allCollected) {
            require(payment.getStatus() == Payment.Status.PENDING && payment.getPaidAt() == null
                    && "UNPAID".equals(order.getPaymentStatus()), "Thanh toán COD đã chốt khi chưa thu đủ tiền.");
            return false;
        }
        require(collected.compareTo(due) == 0 && order.getStatus() == Order.Status.COMPLETED
                && order.getCompletedAt() != null, "Đơn tổng chưa hoàn tất hoặc chưa thu đủ COD.");
        if (payment.getStatus() == Payment.Status.PAID) {
            require(payment.getPaidAt() != null && "PAID".equals(order.getPaymentStatus()), "Trạng thái thanh toán COD không đồng nhất.");
            return true;
        }
        require(payment.getPaidAt() == null && "UNPAID".equals(order.getPaymentStatus()), "Trạng thái thanh toán COD không đồng nhất.");
        payment.setStatus(Payment.Status.PAID);
        payment.setPaidAt(paidAt);
        order.setPaymentStatus("PAID");
        return true;
    }

    private boolean isCollected(ShopOrder child, Delivery delivery) {
        return "COMPLETED".equals(child.getStatus()) && child.getCompletedAt() != null
                && delivery != null && delivery.getStatus() == DeliveryStatus.DELIVERED
                && delivery.getPickedUpAt() != null && delivery.getDeliveredAt() != null
                && delivery.getFailureReason() == null && delivery.getRedeliveryRequestedAt() == null
                && delivery.getRedeliveryScheduledAt() == null;
    }

    private void require(boolean condition, String message) {
        if (!condition) throw conflict(message);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    @Transactional(readOnly = true)
    public boolean isCodOrder(Long orderId) {
        List<Payment> payments = paymentRepository.findByOrderOrderIdOrderByPaymentIdAsc(orderId);
        return !payments.isEmpty() && payments.stream()
                .allMatch(payment -> "COD".equals(payment.getPaymentMethod().getMethodCode()));
    }
}
