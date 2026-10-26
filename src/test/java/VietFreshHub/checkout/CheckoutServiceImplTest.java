package VietFreshHub.checkout;

import VietFreshHub.auth.entity.*;
import VietFreshHub.auth.repository.UserRepository;
import VietFreshHub.cart.dto.*;
import VietFreshHub.cart.entity.*;
import VietFreshHub.cart.repository.CartRepository;
import VietFreshHub.cart.service.CartService;
import VietFreshHub.catalog.entity.*;
import VietFreshHub.catalog.service.CatalogService;
import VietFreshHub.checkout.dto.*;
import VietFreshHub.checkout.repository.CheckoutAddressRepository;
import VietFreshHub.checkout.service.*;
import VietFreshHub.checkout.service.impl.CheckoutServiceImpl;
import VietFreshHub.customer.service.CustomerAddressService;
import VietFreshHub.exception.CheckoutException;
import VietFreshHub.order.entity.*;
import VietFreshHub.order.repository.OrderRepository;
import VietFreshHub.order.service.OrderPlacedEvent;
import VietFreshHub.payment.entity.*;
import VietFreshHub.payment.repository.PaymentMethodRepository;
import VietFreshHub.shop.entity.Shop;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockHttpSession;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** UC-17/GBR-16/23 service behavior only: mock persistence does not prove SQL rollback or locks. */
class CheckoutServiceImplTest {
    final UserRepository users=mock(UserRepository.class);
    final CartRepository carts=mock(CartRepository.class);
    final CartService cartService=mock(CartService.class);
    final CheckoutAddressRepository addresses=mock(CheckoutAddressRepository.class);
    final CustomerAddressService customerAddresses=mock(CustomerAddressService.class);
    final PaymentMethodRepository methods=mock(PaymentMethodRepository.class);
    final OrderRepository orders=mock(OrderRepository.class);
    final CatalogService catalog=mock(CatalogService.class);
    final InventoryReservationService inventory=mock(InventoryReservationService.class);
    final CheckoutShippingQuoteService shipping=mock(CheckoutShippingQuoteService.class);
    final EntityManager em=mock(EntityManager.class);
    final ApplicationEventPublisher events=mock(ApplicationEventPublisher.class);
    final CheckoutServiceImpl service=new CheckoutServiceImpl(users,carts,cartService,addresses,customerAddresses,
            methods,orders,catalog,inventory,shipping,new CheckoutOrderService(orders,em,shipping),em,events);
    final User buyer=new User();
    final Address address=new Address();
    final Cart cart=new Cart();
    final PaymentMethod cod=new PaymentMethod();
    final List<Object> persisted=new ArrayList<>();
    long identity=200;

    @BeforeEach void fixture() {
        buyer.setUserId(7L); buyer.setStatus("ACTIVE");
        address.setAddressId(5L); address.setUser(buyer); address.setRecipientName("Nguyễn An"); address.setPhone("0901234567");
        address.setProvince("TP.HCM"); address.setWard("Phường 1"); address.setAddressLine("25 Nguyễn Huệ");
        address.setFormattedAddress("25 Nguyễn Huệ, Phường 1, TP.HCM");
        address.setLatitude(new BigDecimal("10.77")); address.setLongitude(new BigDecimal("106.70"));
        cart.setCartId(11L); cart.setUser(buyer);
        cart.getItems().add(line(1,10,2,"85000"));
        cart.getItems().add(line(2,20,1,"65000"));
        cod.setMethodCode("COD"); cod.setName("Thanh toán khi nhận hàng");
        when(users.findByUserIdForUpdate(7L)).thenReturn(Optional.of(buyer));
        when(carts.findByUserUserIdAndStatus(7L,"ACTIVE")).thenReturn(Optional.of(cart));
        when(cartService.getActiveCart(7L)).thenAnswer(call -> cartView());
        when(addresses.findByUserUserIdOrderByIsDefaultDescAddressIdAsc(7L)).thenReturn(List.of(address));
        when(customerAddresses.getDeliveryAddress(7L,5L)).thenReturn(address);
        when(customerAddresses.isDeliveryLocationComplete(address)).thenReturn(true);
        when(methods.findByMethodCodeAndStatus("COD","ACTIVE")).thenReturn(Optional.of(cod));
        when(catalog.isPurchasable(any())).thenReturn(true);
        when(orders.save(any(Order.class))).thenAnswer(call -> { Order order=call.getArgument(0); order.setOrderId(100L); return order; });
        when(shipping.validateForConfirmation(any(),eq(address),anySet()))
                .thenAnswer(call -> ((CheckoutDraft)call.getArgument(0)).shippingFee());
        when(shipping.distanceSnapshot(any())).thenAnswer(call -> ((BigDecimal)call.getArgument(0)).setScale(1));
        doAnswer(call -> {
            Object entity=call.getArgument(0); persisted.add(entity);
            if(entity instanceof ShopOrder shop) shop.setShopOrderId(identity++);
            if(entity instanceof OrderItem item) item.setOrderItemId(identity++);
            return null;
        }).when(em).persist(any());
    }

    private CartItem line(long id,long shopId,int quantity,String price) {
        var shop=new Shop(); shop.setShopId(shopId); shop.setShopName("Shop "+shopId);
        var product=new CatalogProduct(); product.setProductId(id*100); product.setShop(shop); product.setName("Trái cây "+id);
        var variant=new CatalogVariant(); variant.setVariantId(id*10); variant.setProduct(product);
        variant.setSku("SKU-"+id); variant.setVariantName("Hộp 1kg"); variant.setPrice(new BigDecimal(price));
        var item=new CartItem(); item.setCartItemId(id); item.setCart(cart); item.setVariant(variant);
        item.setQuantity(quantity); item.setUnitPriceSnapshot(new BigDecimal(price)); return item;
    }
    private CartDto cartView() {
        var items=cart.getItems().stream().map(i -> CartItemDto.builder().cartItemId(i.getCartItemId())
                .variantId(i.getVariant().getVariantId()).shopId(i.getVariant().getProduct().getShop().getShopId())
                .quantity(i.getQuantity()).unitPriceSnapshot(i.getVariant().getPrice())
                .itemSubtotal(i.getVariant().getPrice().multiply(BigDecimal.valueOf(i.getQuantity()))).purchasable(true).build()).toList();
        return CartDto.builder().cartId(11L).userId(7L).items(items)
                .totalItems(items.stream().mapToLong(CartItemDto::getQuantity).sum())
                .totalAmount(items.stream().map(CartItemDto::getItemSubtotal).reduce(BigDecimal.ZERO,BigDecimal::add)).build();
    }
    private CheckoutDraft draft(Long... selectedIds) {
        var preview=service.preview(7L,List.of(selectedIds));
        Map<Long,ShopShippingQuote> quotes=new TreeMap<>();
        for(var item:preview.cart().getItems()) quotes.put(item.getShopId(),new ShopShippingQuote(item.getShopId(),
                BigDecimal.ONE,BigDecimal.TEN,address.getLatitude(),address.getLongitude(),new BigDecimal("2000"),new BigDecimal("15000.00")));
        BigDecimal fee=new BigDecimal("15000.00").multiply(BigDecimal.valueOf(quotes.size()));
        var reviewed=new CheckoutPreview(preview.cart(),preview.addresses(),5L,quotes,fee,preview.cart().getTotalAmount().add(fee),true,preview.fingerprint(),null,false);
        return new CheckoutDraftStore().issue(new MockHttpSession(),7L,reviewed);
    }
    private CheckoutRequest request(CheckoutDraft draft) {
        var request=new CheckoutRequest(); request.setAddressId(5L); request.setCheckoutToken(draft.token()); return request;
    }
    private <T> List<T> persisted(Class<T> type) { return persisted.stream().filter(type::isInstance).map(type::cast).toList(); }
    private void assertNoOrderCreated() { verify(orders,never()).save(any()); assertTrue(persisted.isEmpty()); verifyNoInteractions(inventory,events); }

    @Test void previewUsesOnlySelectedItemsAndDoesNotCreateOrderOrReserveStock() {
        var preview=service.preview(7L,List.of(1L));
        assertEquals(1,preview.cart().getItems().size()); assertEquals(2,preview.cart().getTotalItems());
        assertEquals(0,new BigDecimal("170000").compareTo(preview.cart().getTotalAmount()));
        assertEquals(address.getFormattedAddress(),preview.addresses().getFirst().addressText());
        assertTrue(preview.codAvailable()); assertFalse(preview.canConfirm()); // No shipping quote yet.
        assertNoOrderCreated();
    }
    @Test void previewRejectsEmptyOrForeignSelection() {
        assertThrows(CheckoutException.class,() -> service.preview(7L,List.of()));
        assertThrows(CheckoutException.class,() -> service.preview(7L,List.of(999L)));
        assertNoOrderCreated();
    }
    @Test void multiShopConfirmationSnapshotsTotalsAndCreatesUnpaidCodPayment() {
        var draft=draft(1L,2L); var result=service.confirm(7L,draft,request(draft));
        assertEquals(100L,result.orderId()); assertEquals("VF-"+draft.token(),result.orderCode());
        assertEquals(0,new BigDecimal("265000.00").compareTo(result.grandTotal()));
        var shops=persisted(ShopOrder.class); assertEquals(2,shops.size());
        assertEquals(List.of(10L,20L),shops.stream().map(ShopOrder::getShopId).toList());
        assertEquals(0,result.grandTotal().compareTo(shops.stream().map(ShopOrder::getTotalAmount).reduce(BigDecimal.ZERO,BigDecimal::add)));
        assertTrue(shops.stream().allMatch(s -> "PENDING".equals(s.getStatus()) && s.getDiscountTotal().signum()==0));
        var items=persisted(OrderItem.class); assertEquals(2,items.size());
        assertEquals("Trái cây 1",items.getFirst().getProductNameSnapshot()); assertEquals("SKU-1",items.getFirst().getSkuSnapshot());
        assertEquals(2,items.getFirst().getQuantity());
        var snapshot=persisted(OrderAddress.class).getFirst();
        assertEquals(address.getFormattedAddress(),snapshot.getFormattedAddress()); assertEquals(address.getLatitude(),snapshot.getLatitude());
        address.setRecipientName("Changed later"); cart.getItems().getFirst().getVariant().getProduct().setName("Changed later");
        assertEquals("Nguyễn An",snapshot.getRecipientName()); assertEquals("Trái cây 1",items.getFirst().getProductNameSnapshot());
        var payment=persisted(Payment.class).getFirst();
        assertEquals(Payment.Status.PENDING,payment.getStatus()); assertNull(payment.getPaidAt());
        assertSame(cod,payment.getPaymentMethod()); assertEquals(result.grandTotal(),payment.getAmount());
        assertEquals("UNPAID",payment.getOrder().getPaymentStatus()); assertEquals(Order.Status.PENDING,payment.getOrder().getStatus());
        assertEquals(2,persisted(OrderStatusHistory.class).size()); assertEquals("CHECKED_OUT",cart.getStatus());
        verify(inventory).reserve(eq(List.of(new InventoryReservationService.Reservation(items.get(0).getOrderItemId(),10L,2),
                new InventoryReservationService.Reservation(items.get(1).getOrderItemId(),20L,1))),eq(7L));
        var event=org.mockito.ArgumentCaptor.forClass(OrderPlacedEvent.class);
        verify(events).publishEvent(event.capture()); assertEquals(100L,event.getValue().orderId());
        assertEquals(shops.stream().map(ShopOrder::getShopOrderId).toList(),event.getValue().shopOrderIds());
        var sequence=inOrder(inventory,em,events);
        sequence.verify(inventory).reserve(anyList(),eq(7L)); sequence.verify(em).persist(isA(Payment.class));
        sequence.verify(em).flush(); sequence.verify(events).publishEvent(any(OrderPlacedEvent.class));
    }
    @Test void partialCheckoutKeepsUnselectedItemAndReservesOnlySelectedLine() {
        var draft=draft(1L); service.confirm(7L,draft,request(draft));
        assertEquals(List.of(2L),cart.getItems().stream().map(CartItem::getCartItemId).toList());
        assertEquals("ACTIVE",cart.getStatus()); assertEquals(1,persisted(ShopOrder.class).size());
        assertEquals(0,new BigDecimal("185000").compareTo(persisted(Payment.class).getFirst().getAmount()));
        verify(inventory).reserve(eq(List.of(new InventoryReservationService.Reservation(
                persisted(OrderItem.class).getFirst().getOrderItemId(),10L,2))),eq(7L));
    }
    @Test void repeatedConfirmationReturnsExistingOrderWithoutFurtherWrites() {
        var draft=draft(1L); var existing=new Order(); existing.setOrderId(42L); existing.setOrderCode("VF-"+draft.token()); existing.setGrandTotal(new BigDecimal("185000"));
        when(orders.findByOrderCodeAndCustomerUserId(existing.getOrderCode(),7L)).thenReturn(Optional.of(existing));
        assertEquals(42L,service.confirm(7L,draft,request(draft)).orderId()); assertNoOrderCreated();
    }
    @Test void customerMismatchOrTokenTamperingStopsBeforeLockingBuyer() {
        var draft=draft(1L); var request=request(draft); request.setCheckoutToken("0".repeat(32));
        assertThrows(CheckoutException.class,() -> service.confirm(7L,draft,request));
        assertThrows(CheckoutException.class,() -> service.confirm(8L,draft,request(draft)));
        verify(users,never()).findByUserIdForUpdate(anyLong()); assertNoOrderCreated();
    }
    @Test void changedAddressRequiresNewQuote() {
        var draft=draft(1L); var request=request(draft); request.setAddressId(6L);
        assertThrows(CheckoutException.class,() -> service.confirm(7L,draft,request)); assertNoOrderCreated();
    }
    @Test void inactiveBuyerCannotConfirm() {
        var draft=draft(1L); buyer.setStatus("BLOCKED");
        assertThrows(CheckoutException.class,() -> service.confirm(7L,draft,request(draft))); assertNoOrderCreated();
    }
    @Test void unsupportedPaymentOrUnavailableCodIsRejected() {
        var draft=draft(1L); var request=request(draft); request.setPaymentMethodCode("BANK_TRANSFER");
        assertThrows(CheckoutException.class,() -> service.confirm(7L,draft,request));
        cod.setStatus("INACTIVE");
        assertThrows(CheckoutException.class,() -> service.confirm(7L,draft,request(draft))); assertNoOrderCreated();
    }
    @Test void foreignAddressAndIncompleteLocationCannotConfirm() {
        var draft=draft(1L); var other=new User(); other.setUserId(8L); address.setUser(other);
        assertThrows(CheckoutException.class,() -> service.confirm(7L,draft,request(draft)));
        address.setUser(buyer); when(customerAddresses.isDeliveryLocationComplete(address)).thenReturn(false);
        assertThrows(CheckoutException.class,() -> service.confirm(7L,draft,request(draft))); assertNoOrderCreated();
    }
    @Test void removedSelectedLineCannotBeCheckedOut() {
        var draft=draft(1L); cart.getItems().removeFirst();
        assertThrows(CheckoutException.class,() -> service.confirm(7L,draft,request(draft))); assertNoOrderCreated();
    }
    @Test void closedShopOrUnavailableVariantIsRejectedByCatalogRevalidation() {
        var draft=draft(1L); when(catalog.isPurchasable(any())).thenReturn(false);
        assertThrows(CheckoutException.class,() -> service.confirm(7L,draft,request(draft))); assertNoOrderCreated();
    }
    @Test void changedPriceOrQuantityInvalidatesReviewedFingerprint() {
        var draft=draft(1L); cart.getItems().getFirst().getVariant().setPrice(new BigDecimal("90000"));
        assertThrows(CheckoutException.class,() -> service.confirm(7L,draft,request(draft)));
        cart.getItems().getFirst().getVariant().setPrice(new BigDecimal("85000")); cart.getItems().getFirst().setQuantity(3);
        assertThrows(CheckoutException.class,() -> service.confirm(7L,draft,request(draft))); assertNoOrderCreated();
    }
    @Test void staleShippingStopsBeforeCreatingOrder() {
        var draft=draft(1L); when(shipping.validateForConfirmation(any(),any(),anySet())).thenThrow(new CheckoutException("Stale quote"));
        assertThrows(CheckoutException.class,() -> service.confirm(7L,draft,request(draft))); assertNoOrderCreated();
    }
    @Test void reservationFailurePropagatesWithoutPaymentCartCompletionOrSuccessEvent() {
        var draft=draft(1L); doThrow(new CheckoutException("Insufficient stock")).when(inventory).reserve(anyList(),eq(7L));
        assertThrows(CheckoutException.class,() -> service.confirm(7L,draft,request(draft)));
        assertTrue(persisted(Payment.class).isEmpty()); assertEquals("ACTIVE",cart.getStatus()); assertEquals(2,cart.getItems().size());
        verifyNoInteractions(events); verify(em,never()).flush();
        // Earlier mock persist calls exist; a real DB test must prove their rollback.
    }
    @Test void flushFailureDoesNotPublishOrderPlaced() {
        var draft=draft(1L); doThrow(new IllegalStateException("Flush failed")).when(em).flush();
        assertThrows(IllegalStateException.class,() -> service.confirm(7L,draft,request(draft)));
        verifyNoInteractions(events); // No claim that mock entities were rolled back.
    }
}
