package VietFreshHub.order.repository;

import VietFreshHub.order.entity.Order;
import VietFreshHub.order.entity.ShopOrder;
import VietFreshHub.payment.entity.Payment;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Customer detail projections. No changes to the fulfillment/lifecycle fetch plan. */
public interface CustomerOrderReadRepository extends Repository<Order, Long> {
    @Query("select coalesce(sum(cast(i.quantity as long)),0) from CartItem i "
            + "where i.cart.user.userId=:customerId and i.cart.status='ACTIVE'")
    Long countActiveCartUnits(@Param("customerId") Long customerId);
    interface ShopSummary {
        Long getShopOrderId();
        String getShopName();
        String getStatus();
        BigDecimal getSubtotal();
        BigDecimal getDiscountTotal();
        BigDecimal getShippingFee();
        BigDecimal getTotalAmount();
    }
    interface ItemSnapshot {
        Long getShopOrderId();
        String getProductName();
        String getVariantName();
        String getSku();
        String getImageUrl();
        Integer getQuantity();
        BigDecimal getUnitPrice();
        BigDecimal getDiscountAmount();
        BigDecimal getTotalAmount();
    }
    interface PaymentSummary {
        Long getPaymentId();
        String getMethodName();
        String getMethodCode();
        Payment.Status getStatus();
        BigDecimal getAmount();
        LocalDateTime getPaidAt();
    }
    @Query("select s.shopOrderId as shopOrderId, s.shop.shopName as shopName, s.status as status, "
            + "s.subtotal as subtotal, s.discountTotal as discountTotal, s.shippingFee as shippingFee, s.totalAmount as totalAmount "
            + "from ShopOrder s where s.order.orderId=:orderId and s.order.customer.userId=:customerId order by s.shopOrderId")
    List<ShopSummary> findShops(@Param("customerId") Long customerId, @Param("orderId") Long orderId);

    @Query("select i.shopOrder.shopOrderId as shopOrderId, i.productNameSnapshot as productName, "
            + "i.variantNameSnapshot as variantName, i.skuSnapshot as sku, i.variant.thumbnailUrl as imageUrl, "
            + "i.quantity as quantity, i.unitPrice as unitPrice, i.discountAmount as discountAmount, i.totalAmount as totalAmount "
            + "from OrderItem i where i.shopOrder.order.orderId=:orderId and i.shopOrder.order.customer.userId=:customerId "
            + "order by i.shopOrder.shopOrderId, i.orderItemId")
    List<ItemSnapshot> findItems(@Param("customerId") Long customerId, @Param("orderId") Long orderId);

    @Query("select p.paymentId as paymentId, p.paymentMethod.name as methodName, p.paymentMethod.methodCode as methodCode, "
            + "p.status as status, p.amount as amount, p.paidAt as paidAt from Payment p "
            + "where p.order.orderId=:orderId and p.order.customer.userId=:customerId order by p.paymentId desc")
    List<PaymentSummary> findPayments(@Param("customerId") Long customerId, @Param("orderId") Long orderId);
}
