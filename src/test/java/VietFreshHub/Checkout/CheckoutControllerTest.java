package VietFreshHub.Checkout;

import VietFreshHub.Auth.service.CurrentCustomerService;
import VietFreshHub.Cart.dto.*;
import VietFreshHub.Cart.service.CartService;
import VietFreshHub.Checkout.controller.CheckoutController;
import VietFreshHub.Checkout.dto.*;
import VietFreshHub.Checkout.exception.CheckoutException;
import VietFreshHub.Checkout.service.*;
import VietFreshHub.Order.controller.CustomerOrderDetailsController;
import VietFreshHub.Order.dto.OrderDetailsResponse;
import VietFreshHub.Order.service.CustomerOrderQueryService;
import VietFreshHub.config.SecurityConfig;
import VietFreshHub.exception.ResourceNotFoundException;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

@WebMvcTest({CheckoutController.class,CustomerOrderDetailsController.class})
@Import({SecurityConfig.class,CheckoutDraftStore.class})
class CheckoutControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean CheckoutService checkout;
    @MockitoBean CurrentCustomerService currentCustomer;
    @MockitoBean CartService carts;
    @MockitoBean CustomerOrderQueryService orders;
    @MockitoBean VietFreshHub.Order.service.CustomerOrderService lifecycle;
    @MockitoBean CheckoutAddressService checkoutAddresses;
    CheckoutPreview preview;
    @BeforeEach void setup() {
        when(currentCustomer.requireCustomerId(any())).thenReturn(7L);
        var a=CartItemDto.builder().cartItemId(1L).variantId(10L).shopId(2L).shopName("Vườn Nhà Fresh").productName("Bưởi da xanh")
                .variantName("Hộp 1kg").quantity(2).unitPriceSnapshot(new BigDecimal("79000")).itemSubtotal(new BigDecimal("158000"))
                .imageUrl("/images/buoi-da-xanh.jpg").purchasable(true).quantityEditable(true).availableStock(10).build();
        var b=CartItemDto.builder().cartItemId(2L).variantId(20L).shopId(3L).shopName("Miệt Vườn Fruit").productName("Xoài cát")
                .variantName("Hộp 1kg").quantity(1).unitPriceSnapshot(new BigDecimal("65000")).itemSubtotal(new BigDecimal("65000"))
                .imageUrl("/images/xoai-cat.png").purchasable(true).quantityEditable(true).availableStock(8).build();
        CartDto cart=CartDto.builder().cartId(1L).userId(7L).totalItems(3).items(List.of(a,b)).totalAmount(new BigDecimal("223000")).build();
        preview=new CheckoutPreview(cart,List.of(new CheckoutPreview.AddressOption(1L,"Người nhận","0900000000","Đường mẫu, Hà Nội",true)),
                new BigDecimal("20000.00"),new BigDecimal("40000.00"),new BigDecimal("263000.00"),true,"review-fingerprint");
        when(checkout.preview(7L)).thenReturn(preview);
        when(checkout.preview(eq(7L),anyList())).thenReturn(preview);
        when(carts.getActiveCart(7L)).thenReturn(CartDto.builder().totalItems(0).totalAmount(BigDecimal.ZERO).build());
    }
    private MvcResult page(MockHttpSession session) throws Exception {
        return mvc.perform(get("/customer/checkout").session(session).with(user("buyer@example.com").roles("CUSTOMER")))
                .andExpect(status().isOk()).andReturn();
    }
    private String token(MvcResult result) { return ((CheckoutRequest)result.getModelAndView().getModel().get("form")).getCheckoutToken(); }
    @Test void guestAndOtherRoleCannotCheckout() throws Exception {
        mvc.perform(get("/customer/checkout")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/customer/checkout").with(user("manager").roles("STORE_MANAGER"))).andExpect(status().isForbidden());
        verifyNoInteractions(checkout);
    }
    @Test void previewRendersAddressCodAndTwoShopShipping() throws Exception {
        when(carts.getActiveCart(7L)).thenReturn(CartDto.builder().totalItems(5).totalAmount(BigDecimal.ZERO).build());
        var result=page(new MockHttpSession()); String html=result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(html.contains("263.000")); assertTrue(html.contains("40.000")); assertTrue(html.contains("name=\"_csrf\""));
        assertTrue(html.contains("class=\"badge\">5</span>"));
        assertTrue(token(result).matches("[0-9a-f]{32}"));
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/checkout-preview.html"),html);
    }
    @Test void csrfRequiredForConfirmation() throws Exception {
        mvc.perform(post("/customer/checkout").with(user("buyer").roles("CUSTOMER")).param("addressId","1"))
                .andExpect(status().isForbidden()); verify(checkout,never()).confirm(anyLong(),any(),any());
    }
    @Test void confirmationUsesServerDraftAndIgnoresClientTotalsOrIdentity() throws Exception {
        MockHttpSession session=new MockHttpSession(); String token=token(page(session));
        when(checkout.confirm(eq(7L),any(),any())).thenReturn(new CheckoutResult(10L,"VF-"+token,new BigDecimal("263000")));
        mvc.perform(post("/customer/checkout").session(session).with(user("buyer").roles("CUSTOMER")).with(csrf())
                .param("addressId","1").param("paymentMethodCode","COD").param("checkoutToken",token)
                .param("grandTotal","1").param("shippingFee","0").param("customerId","999"))
                .andExpect(redirectedUrl("/customer/orders/10/confirmation"));
        var capture=ArgumentCaptor.forClass(CheckoutDraft.class); verify(checkout).confirm(eq(7L),capture.capture(),any());
        assertEquals(new BigDecimal("20000.00"),capture.getValue().shippingFeePerShop()); assertEquals("review-fingerprint",capture.getValue().fingerprint());
    }
    @Test void unknownTokenRejectedAndFreshTokenIssued() throws Exception {
        var result=mvc.perform(post("/customer/checkout").with(user("buyer").roles("CUSTOMER")).with(csrf())
                .param("addressId","1").param("paymentMethodCode","COD").param("checkoutToken","f".repeat(32)))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Phiên đặt hàng không hợp lệ"))).andReturn();
        assertNotEquals("f".repeat(32),token(result)); verify(checkout,never()).confirm(anyLong(),any(),any());
    }
    @Test void validationAndBusinessFailurePreserveAddressAndRefreshReview() throws Exception {
        MockHttpSession session=new MockHttpSession(); String token=token(page(session));
        when(checkout.confirm(eq(7L),any(),any())).thenThrow(new CheckoutException("Giá vừa thay đổi. Vui lòng xem lại đơn."));
        var result=mvc.perform(post("/customer/checkout").session(session).with(user("buyer").roles("CUSTOMER")).with(csrf())
                .param("addressId","1").param("checkoutToken",token).param("paymentMethodCode","COD"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Giá vừa thay đổi"))).andReturn();
        assertEquals(1L,((CheckoutRequest)result.getModelAndView().getModel().get("form")).getAddressId());
    }
    @Test void unavailableCartAndMissingAddressDisableConfirmation() throws Exception {
        preview.cart().setHasUnavailableItems(true);
        var unavailable=new CheckoutPreview(preview.cart(),List.of(),preview.shippingFeePerShop(),preview.shippingFee(),preview.grandTotal(),true,preview.fingerprint());
        when(checkout.preview(7L)).thenReturn(unavailable);
        mvc.perform(get("/customer/checkout").with(user("buyer").roles("CUSTOMER"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Bạn chưa có địa chỉ")))
                .andExpect(content().string(containsString("disabled=\"disabled\"")));
    }
    @Test void missingAddressIsValidatedBeforeCreatingOrder() throws Exception {
        MockHttpSession session=new MockHttpSession(); String token=token(page(session));
        mvc.perform(post("/customer/checkout").session(session).with(user("buyer").roles("CUSTOMER")).with(csrf())
                .param("checkoutToken",token).param("paymentMethodCode","COD"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Vui lòng chọn địa chỉ nhận hàng")));
        verify(checkout,never()).confirm(anyLong(),any(),any());
    }
    @Test void orderConfirmationRendersSnapshotAndUnpaidCod() throws Exception {
        var item=new OrderDetailsResponse.Item("Bưởi da xanh","Hộp 1kg","TEST","/images/buoi-da-xanh.jpg",2,new BigDecimal("79000"),new BigDecimal("158000"));
        var shop=new OrderDetailsResponse.ShopGroup(1L,"Vườn Nhà Fresh","Chờ cửa hàng xác nhận",new BigDecimal("20000"),new BigDecimal("178000"),List.of(item));
        var details=new OrderDetailsResponse(10L,"VF-"+"a".repeat(32),"Chờ cửa hàng xác nhận","Chưa thu tiền","Người nhận","0900000000","Đường mẫu, Hà Nội",
                new BigDecimal("158000"),BigDecimal.ZERO,new BigDecimal("20000"),new BigDecimal("178000"),LocalDateTime.of(2026,10,8,12,0),List.of(shop));
        when(orders.getDetails(7L,10L)).thenReturn(details);
        var result=mvc.perform(get("/customer/orders/10/confirmation").with(user("buyer").roles("CUSTOMER"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Đặt hàng thành công"))).andExpect(content().string(containsString("Chưa thu tiền"))).andReturn();
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/order-confirmation-preview.html"),result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }
    @Test void foreignOrderDoesNotLeakDetails() throws Exception {
        when(orders.getDetails(7L,99L)).thenThrow(new ResourceNotFoundException("Không tìm thấy đơn hàng của bạn."));
        mvc.perform(get("/customer/orders/99").with(user("buyer").roles("CUSTOMER"))).andExpect(status().isNotFound())
                .andExpect(content().string(containsString("Không tìm thấy đơn hàng")));
    }
    @Test void selectedIdsAreStoredServerSideAndClientCannotExpandAtPost() throws Exception {
        MockHttpSession session=new MockHttpSession();
        var single=preview.cart().getItems().getFirst();
        var singleCart=CartDto.builder().cartId(1L).userId(7L).items(List.of(single)).totalItems(2).totalAmount(single.getItemSubtotal()).build();
        when(checkout.preview(7L,List.of(1L))).thenReturn(new CheckoutPreview(singleCart,preview.addresses(),new BigDecimal("20000"),
                new BigDecimal("20000"),new BigDecimal("178000"),true,"one-line-review"));
        var result=mvc.perform(get("/customer/checkout").param("itemIds","1").param("selectionSubmitted","true")
                .session(session).with(user("buyer").roles("CUSTOMER"))).andExpect(status().isOk()).andReturn();
        verify(checkout).preview(7L,List.of(1L));
        String token=token(result);
        when(checkout.confirm(eq(7L),any(),any())).thenReturn(new CheckoutResult(10L,"VF-"+token,new BigDecimal("263000")));
        mvc.perform(post("/customer/checkout").session(session).with(user("buyer").roles("CUSTOMER")).with(csrf())
                .param("addressId","1").param("checkoutToken",token).param("paymentMethodCode","COD").param("itemIds","999"))
                .andExpect(status().is3xxRedirection());
        var draft=ArgumentCaptor.forClass(CheckoutDraft.class); verify(checkout).confirm(eq(7L),draft.capture(),any());
        assertEquals(List.of(1L),draft.getValue().selectedItemIds());
    }
    @Test void newAddressPostRequiresValidDraftAndPreservesSelection() throws Exception {
        MockHttpSession session=new MockHttpSession(); String token=token(page(session));
        when(checkoutAddresses.create(eq(7L),any())).thenReturn(5L);
        mvc.perform(post("/customer/checkout/address").session(session).with(user("buyer").roles("CUSTOMER")).with(csrf())
                .param("checkoutToken",token).param("recipientName","Người nhận").param("phone","0901234567")
                .param("province","Hà Nội").param("district","Cầu Giấy").param("ward","Dịch Vọng").param("addressLine","12 Đường mẫu")
                .param("userId","999")).andExpect(redirectedUrl("/customer/checkout?addressId=5&itemIds=1&itemIds=2"));
        verify(checkoutAddresses).create(eq(7L),any());
    }
    @Test void invalidAddressPreservesInputWithoutCreatingAddress() throws Exception {
        MockHttpSession session=new MockHttpSession(); String token=token(page(session));
        mvc.perform(post("/customer/checkout/address").session(session).with(user("buyer").roles("CUSTOMER")).with(csrf())
                .param("checkoutToken",token).param("recipientName","Người nhận đã nhập").param("phone","invalid"))
                .andExpect(status().isOk()).andExpect(model().attribute("addressForm",org.hamcrest.Matchers.hasProperty("recipientName",org.hamcrest.Matchers.is("Người nhận đã nhập"))))
                .andExpect(content().string(containsString("Vui lòng nhập tỉnh hoặc thành phố")));
        verifyNoInteractions(checkoutAddresses);
    }
}
