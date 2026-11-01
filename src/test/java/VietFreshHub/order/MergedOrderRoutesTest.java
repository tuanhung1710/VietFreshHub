package VietFreshHub.order;

import VietFreshHub.auth.service.CurrentCustomerService;
import VietFreshHub.cart.service.CartService;
import VietFreshHub.order.controller.*;
import VietFreshHub.order.dto.*;
import VietFreshHub.order.service.*;
import VietFreshHub.payment.vnpay.VnpayPaymentService;
import VietFreshHub.refund.service.RefundRequestService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.View;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class MergedOrderRoutesTest {
    @Test void shopAndAggregateIdsAreNotInterchangedAndReceiptCannotBypassShopCancellation() throws Exception {
        var current=mock(CurrentCustomerService.class);
        var auth=mock(Authentication.class);
        var reads=mock(CustomerOrderQueryService.class);
        var lifecycle=mock(CustomerOrderService.class);
        var shopReads=mock(ShopCustomerOrderQueryService.class);
        var shopLifecycle=mock(ShopCustomerOrderService.class);
        var vnpay=mock(VnpayPaymentService.class);
        when(current.requireCustomerId(auth)).thenReturn(1L);
        var receipt=new OrderDetailsResponse(10L,"VF-receipt","Chờ xác nhận","Chưa thu tiền",
                "An","0900000000","HCM",BigDecimal.TEN,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.TEN,null,List.of());
        when(shopReads.getShopDetails(1L,20L)).thenReturn(receipt);
        when(shopReads.getDetails(1L,10L)).thenReturn(receipt);
        when(reads.getDetails(1L,10L)).thenReturn(receipt);
        var mvc=MockMvcBuilders.standaloneSetup(
                new ShopCustomerOrderController(current,shopLifecycle,shopReads,vnpay),
                new ShopCustomerOrderDetailsController(current,shopReads,shopLifecycle,vnpay,mock(RefundRequestService.class)),
                new CustomerOrderController(current,lifecycle,mock(CartService.class),reads),
                new CustomerOrderDetailsController(current,reads,lifecycle))
                .setSingleView(mock(View.class)).build();
        var shop=mvc.perform(get("/customer/orders/20").principal(auth)).andReturn();
        assertEquals("customer/shop-order-detail",shop.getModelAndView().getViewName());
        verify(shopReads).getShopDetails(1L,20L); verify(shopReads).getDetails(1L,10L);
        var full=mvc.perform(get("/customer/order-receipts/10").principal(auth)).andReturn();
        assertEquals("customer/order-detail",full.getModelAndView().getViewName());
        assertFalse(((OrderActionsResponse)full.getModelAndView().getModel().get("actions")).cancellable());
        var cancel=mvc.perform(post("/customer/order-receipts/10/cancel").principal(auth)).andReturn();
        assertEquals("redirect:/customer/orders",cancel.getModelAndView().getViewName());
        verify(lifecycle,never()).cancel(anyLong(),anyLong(),any());
    }
}
