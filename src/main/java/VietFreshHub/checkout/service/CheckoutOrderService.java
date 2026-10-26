package VietFreshHub.checkout.service;

import VietFreshHub.auth.entity.Address;
import VietFreshHub.auth.entity.User;
import VietFreshHub.cart.entity.CartItem;
import VietFreshHub.catalog.entity.CatalogVariant;
import VietFreshHub.checkout.dto.ShopShippingQuote;
import VietFreshHub.order.entity.Order;
import VietFreshHub.order.entity.OrderAddress;
import VietFreshHub.order.entity.OrderItem;
import VietFreshHub.order.entity.OrderStatusHistory;
import VietFreshHub.order.entity.ShopOrder;
import VietFreshHub.order.repository.OrderRepository;
import VietFreshHub.payment.entity.Payment;
import VietFreshHub.payment.entity.PaymentMethod;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static VietFreshHub.checkout.service.CheckoutCartSupport.calculateLineTotal;
import static VietFreshHub.checkout.service.CheckoutCartSupport.calculateSubtotal;
import static VietFreshHub.checkout.service.CheckoutCartSupport.normalizeMoney;

/** Lưu đơn hàng và các bản sao thông tin trong cùng transaction xác nhận đặt hàng. */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class CheckoutOrderService {
    private final OrderRepository orderRepository;
    private final EntityManager entityManager;
    private final CheckoutShippingQuoteService shippingQuoteService;

    public Order createOrder(User buyer, String orderCode, BigDecimal subtotal, BigDecimal shippingFee, Address address) {
        Order order = new Order();
        order.setCustomer(buyer);
        order.setOrderCode(orderCode);
        order.setSubtotal(normalizeMoney(subtotal));
        order.setShippingFee(shippingFee);
        order.setGrandTotal(normalizeMoney(subtotal.add(shippingFee)));
        orderRepository.save(order);
        entityManager.persist(snapshotAddress(order, address));
        return order;
    }

    public CreatedShopOrders createShopOrders(Order order, User buyer, Map<Long, List<CartItem>> itemsByShopId,
                                               Map<Long, ShopShippingQuote> quotesByShopId) {
        List<InventoryReservationService.Reservation> reservations = new ArrayList<>();
        List<Long> shopOrderIds = new ArrayList<>();
        for (List<CartItem> shopItems : itemsByShopId.values()) {
            Long shopId = shopItems.getFirst().getVariant().getProduct().getShop().getShopId();
            ShopOrder shopOrder = createShopOrder(order, shopItems, quotesByShopId.get(shopId));
            shopOrderIds.add(shopOrder.getShopOrderId());
            for (CartItem cartItem : shopItems) {
                OrderItem orderItem = createOrderItem(shopOrder, cartItem);
                reservations.add(new InventoryReservationService.Reservation(
                        orderItem.getOrderItemId(), cartItem.getVariant().getVariantId(), cartItem.getQuantity()));
            }
            recordPlacedHistory(shopOrder, buyer);
        }
        return new CreatedShopOrders(shopOrderIds, reservations);
    }

    private ShopOrder createShopOrder(Order order, List<CartItem> shopItems, ShopShippingQuote shippingQuote) {
        ShopOrder shopOrder = new ShopOrder();
        shopOrder.setOrder(order);
        shopOrder.setShop(shopItems.getFirst().getVariant().getProduct().getShop());
        BigDecimal shippingFee = shippingQuote.shippingFee();
        shopOrder.setShippingDistanceMeters(shippingQuoteService.distanceSnapshot(shippingQuote.distanceMeters()));
        BigDecimal subtotal = calculateSubtotal(shopItems);
        shopOrder.setSubtotal(normalizeMoney(subtotal));
        shopOrder.setShippingFee(shippingFee);
        shopOrder.setTotalAmount(normalizeMoney(subtotal.add(shippingFee)));
        entityManager.persist(shopOrder);
        return shopOrder;
    }

    private OrderItem createOrderItem(ShopOrder shopOrder, CartItem cartItem) {
        CatalogVariant variant = cartItem.getVariant();
        OrderItem orderItem = new OrderItem();
        orderItem.setShopOrder(shopOrder);
        orderItem.setVariant(variant);
        orderItem.setProductNameSnapshot(variant.getProduct().getName());
        orderItem.setVariantNameSnapshot(variant.getVariantName());
        orderItem.setSkuSnapshot(variant.getSku());
        orderItem.setUnitPrice(variant.getPrice());
        orderItem.setQuantity(cartItem.getQuantity());
        orderItem.setTotalAmount(normalizeMoney(calculateLineTotal(cartItem)));
        entityManager.persist(orderItem);
        return orderItem;
    }

    private void recordPlacedHistory(ShopOrder shopOrder, User buyer) {
        OrderStatusHistory history = new OrderStatusHistory();
        history.setShopOrder(shopOrder);
        history.setStatus("PENDING");
        history.setChangedBy(buyer);
        history.setNote("Khách hàng đặt đơn COD.");
        entityManager.persist(history);
    }

    public void createPendingPayment(Order order, PaymentMethod paymentMethod) {
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setPaymentMethod(paymentMethod);
        payment.setAmount(order.getGrandTotal());
        entityManager.persist(payment);
    }

    private OrderAddress snapshotAddress(Order order, Address address) {
        OrderAddress snapshot = new OrderAddress();
        snapshot.setOrder(order);
        snapshot.setRecipientName(address.getRecipientName());
        snapshot.setPhone(address.getPhone());
        snapshot.setProvince(address.getProvince());
        snapshot.setDistrict(address.getDistrict());
        snapshot.setWard(address.getWard());
        snapshot.setAddressLine(address.getAddressLine());
        snapshot.setProvinceId(address.getProvinceId());
        snapshot.setDistrictId(address.getDistrictId());
        snapshot.setWardId(address.getWardId());
        snapshot.setFormattedAddress(address.getFormattedAddress());
        snapshot.setLatitude(address.getLatitude());
        snapshot.setLongitude(address.getLongitude());
        return snapshot;
    }

    public record CreatedShopOrders(
            List<Long> shopOrderIds,
            List<InventoryReservationService.Reservation> reservations
    ) {
    }
}
