package VietFreshHub.checkout;

import VietFreshHub.auth.entity.*;
import VietFreshHub.auth.repository.UserRepository;
import VietFreshHub.cart.entity.*;
import VietFreshHub.cart.repository.CartRepository;
import VietFreshHub.cart.service.CartService;
import VietFreshHub.catalog.entity.*;
import VietFreshHub.catalog.repository.CatalogVariantRepository;
import VietFreshHub.catalog.service.CatalogService;
import VietFreshHub.checkout.dto.*;
import VietFreshHub.checkout.repository.*;
import VietFreshHub.checkout.service.*;
import VietFreshHub.checkout.service.impl.CheckoutServiceImpl;
import VietFreshHub.customer.service.CustomerAddressService;
import VietFreshHub.order.entity.Order;
import VietFreshHub.order.repository.OrderRepository;
import VietFreshHub.payment.entity.*;
import VietFreshHub.payment.repository.*;
import VietFreshHub.payment.vnpay.VnpayProperties;
import VietFreshHub.shop.entity.Shop;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockHttpSession;
import org.junit.jupiter.api.*;
import org.mockito.*;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BuyNowCheckoutTest {
    @Mock CatalogVariantRepository buyNowVariants;
    @Mock CheckoutInventoryBatchRepository buyNowStock;
    @Mock UserRepository users;
    @Mock CartRepository carts;
    @Mock CartService cartService;
    @Mock CheckoutAddressRepository addresses;
    @Mock CustomerAddressService customerAddresses;
    @Mock PaymentMethodRepository methods;
    @Mock OrderRepository orders;
    @Mock CatalogService catalog;
    @Mock InventoryReservationService inventory;
    @Mock CheckoutShippingQuoteService shipping;
    @Mock CheckoutOrderService orderWriter;
    @Mock EntityManager em;
    @Mock ApplicationEventPublisher events;
    @Mock PaymentRepository payments;
    @Mock VnpayProperties vnpay;
    @Mock VietFreshHub.auth.service.CurrentCustomerService currentCustomer;
    @InjectMocks VietFreshHub.cart.controller.CustomerCartViewController cartController;
    @InjectMocks CheckoutServiceImpl service;
    AutoCloseable mocks;
    CatalogVariant variant;
    Cart existingCart;
    @BeforeEach void setup() {
        mocks=MockitoAnnotations.openMocks(this);
        var shop=new Shop();shop.setShopId(3L);shop.setShopName("Shop");
        var product=new CatalogProduct();product.setProductId(5L);product.setName("Chuối");product.setShop(shop);
        variant=new CatalogVariant();variant.setVariantId(7L);variant.setVariantName("1kg");variant.setProduct(product);variant.setPrice(new BigDecimal("45000"));
        when(buyNowVariants.findById(7L)).thenReturn(Optional.of(variant));when(buyNowStock.getAvailableStockByVariantId(7L)).thenReturn(10);when(catalog.isPurchasable(variant)).thenReturn(true);
        existingCart=new Cart();var line=new CartItem();line.setVariant(variant);line.setQuantity(2);existingCart.setItems(new ArrayList<>(List.of(line)));
    }
    @AfterEach void close() throws Exception {mocks.close();}
    @Test void previewUsesRequestedQuantityWithoutReadingOrChangingCart() {
        var preview=service.previewBuyNow(1L,7L,1);
        assertEquals(1,preview.cart().getItems().getFirst().getQuantity());assertEquals(new BigDecimal("45000"),preview.cart().getTotalAmount());
        assertEquals(2,existingCart.getItems().getFirst().getQuantity());verifyNoInteractions(carts,cartService);
    }
    @Test void confirmationOnlyOrdersBuyNowQuantityAndLeavesCartAlone() {
        var preview=service.previewBuyNow(1L,7L,1);
        var buyer=new User();buyer.setUserId(1L);buyer.setStatus("ACTIVE");when(users.findByUserIdForUpdate(1L)).thenReturn(Optional.of(buyer));
        var address=new Address();address.setAddressId(2L);address.setUser(buyer);when(customerAddresses.getDeliveryAddress(1L,2L)).thenReturn(address);when(customerAddresses.isDeliveryLocationComplete(address)).thenReturn(true);
        var method=new PaymentMethod();method.setMethodCode("COD");method.setStatus("ACTIVE");when(methods.findByMethodCodeAndStatus("COD","ACTIVE")).thenReturn(Optional.of(method));
        var draft=new CheckoutDraft("a".repeat(32),1L,null,preview.fingerprint(),2L,Map.of(),BigDecimal.ZERO,List.of(7L),7L,1);
        var request=new CheckoutRequest();request.setCheckoutToken(draft.token());request.setAddressId(2L);
        when(shipping.validateForConfirmation(eq(draft),eq(address),any())).thenReturn(BigDecimal.ZERO);
        var order=new Order();order.setOrderId(8L);order.setOrderCode("VF-test");order.setGrandTotal(new BigDecimal("45000"));
        when(orderWriter.createOrder(eq(buyer),anyString(),eq(new BigDecimal("45000")),eq(BigDecimal.ZERO),eq(address))).thenReturn(order);
        when(orderWriter.createShopOrders(eq(order),eq(buyer),any(),any())).thenAnswer(call->{Map<Long,List<CartItem>> selection=call.getArgument(2);assertEquals(1,selection.get(3L).getFirst().getQuantity());return new CheckoutOrderService.CreatedShopOrders(List.of(9L),List.of());});
        var payment=new Payment();payment.setPaymentId(10L);payment.setPaymentMethod(method);when(orderWriter.createPendingPayment(order,method)).thenReturn(payment);
        assertEquals(8L,service.confirm(1L,draft,request).orderId());
        assertEquals(2,existingCart.getItems().getFirst().getQuantity());verifyNoInteractions(carts,cartService);
        verify(inventory).reserve(List.of(),1L);
        when(orders.findByOrderCodeAndCustomerUserId("VF-"+draft.token(),1L)).thenReturn(Optional.of(order));
        when(payments.findByOrderOrderIdOrderByPaymentIdAsc(8L)).thenReturn(List.of(payment));
        assertEquals(8L,service.confirm(1L,draft,request).orderId());
        verify(orderWriter,times(1)).createShopOrders(eq(order),eq(buyer),any(),any());
    }
    @Test void freshPurchaseHasNewTokenButReloadPreservesTokenAndOwnership() {
        var preview=service.previewBuyNow(1L,7L,1);var store=new CheckoutDraftStore();var session=new MockHttpSession();
        var first=store.issueBuyNow(session,1L,preview,null);var second=store.issueBuyNow(session,1L,preview,null);
        assertNotEquals(first.token(),second.token());assertTrue(first.buyNow());assertEquals(1,first.buyNowQuantity());
        assertEquals(first.token(),store.issueBuyNow(session,1L,preview,first.token()).token());assertNull(store.find(session,first.token(),2L));
    }
    @Test void invalidQuantityAndInsufficientStockCannotBeConfirmedInPreview() {
        assertThrows(VietFreshHub.exception.CheckoutException.class,()->service.previewBuyNow(1L,7L,0));
        assertTrue(service.previewBuyNow(1L,7L,11).cart().isHasUnavailableItems());verifyNoInteractions(carts,cartService);
    }
    @Test void buyNowButtonBypassesCartAddition() {
        var form=new VietFreshHub.cart.dto.AddToCartRequest();form.setVariantId(7L);form.setQuantity(1);
        var auth=mock(org.springframework.security.core.Authentication.class);when(currentCustomer.requireCustomerId(auth)).thenReturn(1L);
        var redirect=cartController.handleAddToCart(form,new org.springframework.validation.BeanPropertyBindingResult(form,"form"),auth,"checkout",new org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap());
        assertEquals("redirect:/customer/checkout?buyNowVariantId=7&buyNowQuantity=1",redirect);verifyNoInteractions(cartService);
    }
    @Test void addressRequoteKeepsBuyNowIntentAndOriginalDraftIndependent() {
        var preview=service.previewBuyNow(1L,7L,1);var store=new CheckoutDraftStore();var session=new MockHttpSession();
        var original=store.issueBuyNow(session,1L,preview,null);
        var requoted=new CheckoutPreview(preview.cart(),preview.addresses(),2L,Map.of(),BigDecimal.TEN,new BigDecimal("45010"),true,true,preview.fingerprint(),null,false);
        var updated=store.issueBuyNow(session,1L,requoted,original.token());
        assertTrue(updated.buyNow());assertEquals(7L,updated.buyNowVariantId());assertEquals(1,updated.buyNowQuantity());assertEquals(2L,updated.addressId());
        assertNotEquals(original.token(),updated.token());assertNull(store.find(session,original.token(),1L).addressId());
        verifyNoInteractions(carts,cartService);
    }
}
