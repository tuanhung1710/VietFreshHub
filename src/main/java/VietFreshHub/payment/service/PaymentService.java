package VietFreshHub.payment.service;

import VietFreshHub.delivery.entity.Delivery;
import VietFreshHub.delivery.entity.DeliveryStatus;
import VietFreshHub.delivery.service.DeliveryReturnQueryService;
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
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderLifecycleLock lifecycleLock;
    private final DeliveryReturnQueryService deliveryReturnQueryService;
    private final EntityManager em;

    /**
     * One order-level COD Payment settles the totals of all collectible ShopOrders.
     * Each ShopOrder is counted once from its delivery evidence in this transaction.
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
        List<ShopOrder> collectible = deliveryReturnQueryService.collectibleCodShopOrders(orderId, children);
        validateCodPayment(order, payment, children, collectible, true, false);
        require(collectible.stream().anyMatch(child -> child.getShopOrderId().equals(shopOrderId)),
                "Đơn cửa hàng đã hủy hoặc trả hàng, không thể thu COD.");

        BigDecimal due = collectible.stream().map(ShopOrder::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Delivery> evidence = em.createQuery("select d from Delivery d where d.shopOrder.order.orderId = :id", Delivery.class)
                .setParameter("id", orderId).getResultList();
        evidence.forEach(em::refresh);
        var deliveries = evidence.stream()
                .collect(Collectors.toMap(d -> d.getShopOrder().getShopOrderId(), Function.identity()));
        require(isCollected(target, deliveries.get(shopOrderId)), "Chưa có bằng chứng giao thành công và thu đủ COD.");
        BigDecimal collected = BigDecimal.ZERO;
        boolean allCollected = true;
        LocalDateTime paidAt = null;
        for (ShopOrder child : collectible) {
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
        require(collected.compareTo(due) == 0
                && (order.getStatus() == Order.Status.COMPLETED || order.getStatus() == Order.Status.PARTIALLY_COMPLETED)
                && (order.getStatus() != Order.Status.COMPLETED || order.getCompletedAt() != null),
                "Các đơn còn nghĩa vụ COD chưa hoàn tất hoặc chưa thu đủ tiền.");
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

    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional(readOnly = true)
    public Optional<BigDecimal> getPaidOnlineOrderAmount(Long orderId) {
        Payment paidPayment = null;
        for (Payment payment : paymentRepository.findByOrderOrderIdOrderByPaymentIdAsc(orderId)) {
            if (payment.getStatus() == Payment.Status.PROCESSING
                    || payment.getStatus() == Payment.Status.REFUNDED
                    || payment.getStatus() == Payment.Status.PARTIALLY_REFUNDED) {
                return Optional.empty();
            }
            if (payment.getStatus() == Payment.Status.PAID) {
                if (paidPayment != null) {
                    return Optional.empty();
                }
                paidPayment = payment;
            }
        }
        if (paidPayment == null || paidPayment.getPaymentMethod() == null || paidPayment.getPaidAt() == null
                || paidPayment.getAmount() == null || paidPayment.getAmount().signum() <= 0) {
            return Optional.empty();
        }
        String method = paidPayment.getPaymentMethod().getMethodCode();
        boolean online = "VNPAY".equals(method)
                || ("BANK_TRANSFER".equals(method) && "VNPAY".equals(paidPayment.getProvider()));
        return online ? Optional.of(paidPayment.getAmount()) : Optional.empty();
    }

    @Transactional(readOnly = true)
    public boolean isCodOrder(Long orderId) {
        List<Payment> payments = paymentRepository.findByOrderOrderIdOrderByPaymentIdAsc(orderId);
        return !payments.isEmpty() && payments.stream()
                .allMatch(payment -> payment.getPaymentMethod() != null
                        && "COD".equals(payment.getPaymentMethod().getMethodCode()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void validatePaymentForDelivery(Long orderId) {
        Order order = lifecycleLock.lock(orderId);
        List<Payment> payments = paymentRepository.findByOrderOrderIdOrderByPaymentIdAsc(orderId);
        require(!payments.isEmpty() && payments.stream().allMatch(payment -> payment.getOrder() != null
                        && orderId.equals(payment.getOrder().getOrderId())),
                "Đơn hàng cần đúng giao dịch thanh toán được liên kết.");
        boolean online = payments.stream().allMatch(this::isVnpayPayment);
        if (online) {
            require("PAID".equals(order.getPaymentStatus()) && order.getGrandTotal() != null
                            && getPaidOnlineOrderAmount(orderId)
                            .filter(amount -> amount.compareTo(order.getGrandTotal()) == 0).isPresent(),
                    "Thanh toán online chưa được xác nhận hợp lệ.");
            List<ShopOrder> children = em.createQuery(
                    "select s from ShopOrder s where s.order.orderId = :id order by s.shopOrderId", ShopOrder.class)
                    .setParameter("id", orderId).getResultList();
            validateOrderAmounts(order, children);
            return;
        }
        require(payments.size() == 1, "Đơn COD phải có đúng một giao dịch thanh toán.");
        Payment payment = payments.getFirst();
        em.refresh(payment, LockModeType.PESSIMISTIC_WRITE);
        List<ShopOrder> children = em.createQuery(
                "select s from ShopOrder s where s.order.orderId = :id order by s.shopOrderId", ShopOrder.class)
                .setParameter("id", orderId).getResultList();
        validateCodPayment(order, payment, children,
                deliveryReturnQueryService.collectibleCodShopOrders(orderId, children), false, false);
    }

    private void validateCodPayment(Order order, Payment payment, List<ShopOrder> children,
                                    List<ShopOrder> collectible, boolean allowPaid, boolean allowCancelled) {
        require(payment.getOrder() != null && order.getOrderId().equals(payment.getOrder().getOrderId())
                        && payment.getPaymentMethod() != null
                        && "COD".equals(payment.getPaymentMethod().getMethodCode())
                        && !"VNPAY".equals(payment.getProvider()),
                "Phương thức thanh toán không phải COD của đơn hàng này.");
        require(payment.getAmount() != null && payment.getAmount().signum() >= 0,
                "Số tiền COD không hợp lệ.");
        require(payment.getStatus() == Payment.Status.PENDING
                        || (allowPaid && payment.getStatus() == Payment.Status.PAID)
                        || (allowCancelled && payment.getStatus() == Payment.Status.CANCELLED),
                "Giao dịch COD không còn đủ điều kiện thu tiền.");
        require(payment.getStatus() != Payment.Status.PAID || payment.getPaidAt() != null,
                "Thời điểm thanh toán COD không hợp lệ.");
        validateOrderAmounts(order, children);
        BigDecimal due = collectible.stream().map(ShopOrder::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        require(due.signum() >= 0 && (payment.getAmount().compareTo(due) == 0
                        || (payment.getStatus() != Payment.Status.PAID
                        && payment.getAmount().compareTo(order.getGrandTotal()) == 0)),
                "Số tiền COD không khớp nghĩa vụ thu còn lại.");
        if (payment.getStatus() == Payment.Status.PENDING) {
            require(payment.getPaidAt() == null && Set.of("UNPAID", "PENDING").contains(order.getPaymentStatus()),
                    "Trạng thái thanh toán COD không hợp lệ.");
        } else if (payment.getStatus() == Payment.Status.PAID) {
            require("PAID".equals(order.getPaymentStatus()), "Trạng thái thanh toán COD không đồng nhất.");
        } else {
            require(allowCancelled && collectible.isEmpty() && payment.getPaidAt() == null
                            && !"PAID".equals(order.getPaymentStatus()),
                    "Trạng thái COD đã hủy không đồng nhất.");
        }
    }

    private void validateOrderAmounts(Order order, List<ShopOrder> children) {
        require(order.getSubtotal() != null && order.getSubtotal().signum() >= 0
                        && order.getDiscountTotal() != null && order.getDiscountTotal().signum() >= 0
                        && order.getDiscountTotal().compareTo(order.getSubtotal()) <= 0
                        && order.getShippingFee() != null && order.getShippingFee().signum() >= 0
                        && order.getGrandTotal() != null
                        && order.getSubtotal().subtract(order.getDiscountTotal()).add(order.getShippingFee())
                        .compareTo(order.getGrandTotal()) == 0,
                "Tổng tiền đơn hàng không hợp lệ.");
        BigDecimal subtotal = BigDecimal.ZERO, discount = BigDecimal.ZERO, shipping = BigDecimal.ZERO, total = BigDecimal.ZERO;
        for (ShopOrder child : children) {
            require(child.getSubtotal() != null && child.getSubtotal().signum() >= 0
                            && child.getDiscountTotal() != null && child.getDiscountTotal().signum() >= 0
                            && child.getDiscountTotal().compareTo(child.getSubtotal()) <= 0
                            && child.getShippingFee() != null && child.getShippingFee().signum() >= 0
                            && child.getTotalAmount() != null && child.getTotalAmount().signum() >= 0,
                    "Số tiền đơn cửa hàng không hợp lệ.");
            BigDecimal calculated = child.getSubtotal().subtract(child.getDiscountTotal()).add(child.getShippingFee());
            require(calculated.compareTo(child.getTotalAmount()) == 0, "Tổng tiền đơn cửa hàng không khớp.");
            subtotal = subtotal.add(child.getSubtotal()); discount = discount.add(child.getDiscountTotal());
            shipping = shipping.add(child.getShippingFee()); total = total.add(calculated);
        }
        require(subtotal.compareTo(order.getSubtotal()) == 0 && discount.compareTo(order.getDiscountTotal()) == 0
                        && shipping.compareTo(order.getShippingFee()) == 0 && total.compareTo(order.getGrandTotal()) == 0,
                "Tổng tiền các đơn cửa hàng không khớp đơn hàng.");
    }

    private boolean isVnpayPayment(Payment payment) {
        if (payment.getPaymentMethod() == null) return false;
        String method = payment.getPaymentMethod().getMethodCode();
        return "VNPAY".equals(method) || ("BANK_TRANSFER".equals(method) && "VNPAY".equals(payment.getProvider()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean reconcileCodPayment(Long orderId, BigDecimal amount, LocalDateTime paidAt, boolean noCollectibleOrders) {
        List<Payment> payments = paymentRepository.findByOrderOrderIdOrderByPaymentIdAsc(orderId);
        if (payments.size() != 1 || payments.getFirst().getPaymentMethod() == null
                || !"COD".equals(payments.getFirst().getPaymentMethod().getMethodCode())) {
            return false;
        }
        Payment payment = paymentRepository.findByIdForUpdate(payments.getFirst().getPaymentId())
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy bản ghi thanh toán COD."));
        em.refresh(payment, LockModeType.PESSIMISTIC_WRITE);
        Order order = payment.getOrder();
        List<ShopOrder> children = em.createQuery(
                "select s from ShopOrder s where s.order.orderId = :id order by s.shopOrderId", ShopOrder.class)
                .setParameter("id", orderId).getResultList();
        List<ShopOrder> collectible = deliveryReturnQueryService.collectibleCodShopOrders(orderId, children);
        BigDecimal expected = collectible.stream().map(ShopOrder::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (amount == null || amount.signum() < 0 || amount.compareTo(expected) != 0
                || noCollectibleOrders != collectible.isEmpty()) {
            return false;
        }
        if (noCollectibleOrders && payments.getFirst().getStatus() == Payment.Status.PAID) return false;
        validateCodPayment(order, payment, children, collectible, true, noCollectibleOrders);
        if (noCollectibleOrders) {
            payment.setAmount(BigDecimal.ZERO);
            payment.setStatus(Payment.Status.CANCELLED);
            return false;
        }
        if (payment.getAmount().compareTo(order.getGrandTotal()) != 0
                && payment.getAmount().compareTo(expected) != 0) return false;
        if (payment.getStatus() == Payment.Status.PAID) return true;
        payment.setAmount(amount);
        if (paidAt == null) return false;
        payment.setStatus(Payment.Status.PAID);
        payment.setPaidAt(paidAt);
        return true;
    }

    @Transactional(readOnly = true)
    public String getInvoicePaymentMethodLabel(Long orderId) {
        Payment payment = paymentRepository.findTopByOrderOrderIdAndProviderOrderByPaymentIdDesc(orderId, "VNPAY")
                .or(() -> paymentRepository.findTopByOrderOrderIdAndPaymentMethodMethodCodeOrderByPaymentIdDesc(orderId, "VNPAY"))
                .orElseGet(() -> paymentRepository.findByOrderOrderIdOrderByPaymentIdAsc(orderId).stream()
                        .findFirst().orElse(null));
        if (payment == null) {
            return "Chưa có thông tin";
        }
        return switch (payment.getPaymentMethod().getMethodCode()) {
            case "COD" -> "Thanh toán khi nhận hàng";
            case "BANK_TRANSFER", "VNPAY" -> "Thanh toán online";
            default -> "Chưa xác định";
        };
    }
}
