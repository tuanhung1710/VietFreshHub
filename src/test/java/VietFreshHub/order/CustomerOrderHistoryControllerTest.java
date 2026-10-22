package VietFreshHub.order;

import VietFreshHub.auth.service.CurrentCustomerService;
import VietFreshHub.cart.service.CartService;
import VietFreshHub.order.controller.CustomerOrderController;
import VietFreshHub.order.dto.*;
import VietFreshHub.order.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.View;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Controller binding/flash checks; does not replace security filter-chain integration. */
class CustomerOrderHistoryControllerTest {
    final CurrentCustomerService current = mock(CurrentCustomerService.class);
    final CustomerOrderService orders = mock(CustomerOrderService.class);
    final Authentication authentication = mock(Authentication.class);
    MockMvc mvc;
    @BeforeEach void setup() {
        when(current.requireCustomerId(authentication)).thenReturn(1L);
        mvc = MockMvcBuilders.standaloneSetup(new CustomerOrderController(current, orders,
                mock(CartService.class), mock(CustomerOrderQueryService.class)))
                .setSingleView(mock(View.class)).build();
    }
    @Test void historyOffersDeliveringInsteadOfRemovedFilters() throws Exception {
        when(orders.history(eq(1L), any())).thenReturn(Page.empty());
        var model = mvc.perform(get("/customer/orders").principal(authentication)).andReturn().getModelAndView().getModel();
        Map<?, ?> options = (Map<?, ?>) model.get("statusOptions");
        assertEquals("Chờ giao hàng", options.get("AWAITING_DELIVERY"));
        assertEquals(7, options.size());
        assertEquals("Chờ xác nhận", options.get("WAITING_CONFIRMATION"));
        assertEquals("Đang chuẩn bị", options.get("PREPARING"));
        assertFalse(options.containsKey("RETURN_REFUND"));
        assertFalse(options.containsKey("PROCESSING")); assertFalse(options.containsKey("PARTIALLY_COMPLETED"));
    }
    @Test void malformedPageDoesNotRunHistoryQuery() throws Exception {
        var model = mvc.perform(get("/customer/orders").param("page", "abc").principal(authentication))
                .andReturn().getModelAndView().getModel();
        assertEquals(true, model.get("invalidFilter")); verifyNoInteractions(orders);
    }
    @Test void reversedDatesDoNotRunHistoryQuery() throws Exception {
        var model = mvc.perform(get("/customer/orders").param("fromDate", "2026-10-21")
                .param("toDate", "2026-10-20").principal(authentication)).andReturn().getModelAndView().getModel();
        assertEquals(true, model.get("invalidFilter")); verifyNoInteractions(orders);
    }
    @Test void reorderRedirectsToCartWithDroppedItemsAndCurrentPriceMessage() throws Exception {
        var skipped = List.of(new ReorderResult.SkippedItem("Món cũ", "Loại cũ", "Hết hàng"));
        when(orders.reorder(1L, 10L)).thenReturn(new ReorderResult(1, 2, skipped));
        var result = mvc.perform(post("/customer/orders/10/reorder").principal(authentication)).andReturn();
        assertEquals("redirect:/cart", result.getModelAndView().getViewName());
        assertEquals(skipped, result.getFlashMap().get("reorderSkipped"));
        assertTrue(result.getFlashMap().get("successMessage").toString().contains("giá hiện tại"));
    }
}
