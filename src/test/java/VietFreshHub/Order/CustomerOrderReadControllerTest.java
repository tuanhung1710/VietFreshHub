package VietFreshHub.Order;

import VietFreshHub.Auth.entity.User;
import VietFreshHub.Auth.repository.UserRepository;
import VietFreshHub.Auth.service.CurrentCustomerService;
import VietFreshHub.Cart.dto.CartDto;
import VietFreshHub.Cart.service.CartService;
import VietFreshHub.Order.controller.*;
import VietFreshHub.Order.dto.*;
import VietFreshHub.Order.entity.Order;
import VietFreshHub.Order.service.*;
import VietFreshHub.config.SecurityConfig;
import VietFreshHub.exception.ResourceNotFoundException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

@WebMvcTest({CustomerOrderController.class,CustomerOrderDetailsController.class})
@Import({SecurityConfig.class,CurrentCustomerService.class})
class CustomerOrderReadControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean UserRepository users;
    @MockitoBean CartService carts;
    @MockitoBean CustomerOrderService lifecycle;
    @MockitoBean CustomerOrderQueryService details;
    @BeforeEach void setup() {
        var buyer=new User(); buyer.setUserId(7L); buyer.setEmail("buyer@fe052.invalid"); buyer.setFullName("Khách");
        when(users.findByEmailIgnoreCase(buyer.getEmail())).thenReturn(Optional.of(buyer));
        when(carts.getActiveCart(7L)).thenReturn(CartDto.builder().totalItems(0).totalAmount(BigDecimal.ZERO).build());
        when(lifecycle.actions(eq(7L),anyLong())).thenReturn(new OrderActionsResponse(false,"Đơn không còn được phép hủy.","COD"));
    }
    List<OrderListRow> rows() {
        return List.of(new OrderListRow(1L,"VF-"+"A".repeat(47),Order.Status.PENDING,Order.PaymentState.UNPAID,new BigDecimal("9999999999999999.50"),LocalDateTime.of(2026,10,10,17,0)));
    }
    OrderDetailsResponse invoice(String status,String paymentStatus,List<OrderDetailsResponse.PaymentInfo> payments) {
        var item=new OrderDetailsResponse.Item("Tên snapshot "+"Sản phẩm ".repeat(25),"Phân loại đã mua","SKU-"+"A".repeat(96),"/images/buoi-da-xanh.jpg",2,
                new BigDecimal("20000.25"),new BigDecimal("39000"),new BigDecimal("1000.50"));
        var shop=new OrderDetailsResponse.ShopGroup(12L,"Cửa hàng "+"Tên dài ".repeat(22),"Đang chuẩn bị",new BigDecimal("20000"),new BigDecimal("59000"),List.of(item),new BigDecimal("40000.50"),new BigDecimal("1000.50"));
        return new OrderDetailsResponse(1L,"VF-"+"A".repeat(47),status,paymentStatus,"Người nhận snapshot","0901234567","Địa chỉ snapshot "+"Địa chỉ dài ".repeat(35),
                new BigDecimal("40000.50"),new BigDecimal("1000.50"),new BigDecimal("20000"),new BigDecimal("59000"),LocalDateTime.of(2026,10,11,0,0),List.of(shop),payments);
    }
    void save(String name,String html) throws Exception {
        var dir=Path.of(".document-analysis/fe052/ui"); Files.createDirectories(dir); Files.writeString(dir.resolve(name+".html"),html,java.nio.charset.StandardCharsets.UTF_8);
    }
    @Test void authenticationAndRoleAreRequired() throws Exception {
        mvc.perform(get("/customer/orders")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/customer/orders/1")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/customer/orders").with(user("manager").roles("STORE_MANAGER"))).andExpect(status().isForbidden());
        verifyNoInteractions(lifecycle,details);
    }
    @ParameterizedTest @CsvSource({"page,abc","page,0","page,10001","status,DELIVERED","fromDate,bad-date","toDate,+10000-01-01"})
    void invalidInputDoesNotQueryUnfilteredOrders(String field,String value) throws Exception {
        var result=mvc.perform(get("/customer/orders").with(user("buyer@fe052.invalid").roles("CUSTOMER")).param(field,value))
                .andExpect(status().isOk()).andExpect(model().attribute("invalidFilter",true))
                .andExpect(content().string(containsString("Bộ lọc không hợp lệ"))).andReturn();
        if("page".equals(field) && "abc".equals(value)) assertTrue(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8).contains("Trang phải là số nguyên hợp lệ."));
        assertFalse(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8).contains("java.lang"));
        verifyNoInteractions(lifecycle); save("invalid-filter",result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }
    @Test void reversedDateRangeAndOverlongKeywordKeepInputWithoutFetchingOrders() throws Exception {
        mvc.perform(get("/customer/orders").with(user("buyer@fe052.invalid").roles("CUSTOMER"))
                .param("fromDate","2026-10-13").param("toDate","2026-10-01").param("keyword","MÃ ĐÃ NHẬP"))
                .andExpect(status().isOk()).andExpect(model().attribute("filter",org.hamcrest.Matchers.hasProperty("keyword",org.hamcrest.Matchers.is("MÃ ĐÃ NHẬP"))))
                .andExpect(content().string(containsString("Ngày kết thúc phải bằng hoặc sau ngày bắt đầu.")));
        mvc.perform(get("/customer/orders").with(user("buyer@fe052.invalid").roles("CUSTOMER")).param("keyword","x".repeat(101)))
                .andExpect(status().isOk()).andExpect(model().attribute("invalidFilter",true)); verifyNoInteractions(lifecycle);
    }
    @Test void filtersArePreservedInPaginationAndCustomerIdentityComesFromTv1() throws Exception {
        when(lifecycle.history(eq(7L),any())).thenReturn(new PageImpl<>(rows(),PageRequest.of(1,10),35));
        var result=mvc.perform(get("/customer/orders").with(user("buyer@fe052.invalid").roles("CUSTOMER"))
                .header("X-User-Id","999").param("customerId","999").param("page","2").param("keyword","VF-A")
                .param("status","PENDING").param("fromDate","2026-10-01").param("toDate","2026-10-13"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("page=3")))
                .andExpect(content().string(containsString("keyword=VF-A"))).andExpect(content().string(containsString("status=PENDING")))
                .andExpect(content().string(containsString("fromDate=2026-10-01"))).andExpect(content().string(containsString("toDate=2026-10-13")))
                .andExpect(content().string(containsString("9.999.999.999.999.999,50"))).andReturn();
        verify(lifecycle).history(eq(7L),argThat(f -> f.getPage()==2 && "PENDING".equals(f.getStatus())));
        verify(users).findByEmailIgnoreCase("buyer@fe052.invalid"); verify(details).getCartBadgeTotal(7L); verifyNoInteractions(carts); save("history",result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }
    @Test void emptyAndClampedPageHaveClearMessages() throws Exception {
        when(lifecycle.history(eq(7L),any())).thenReturn(Page.empty(PageRequest.of(0,10)));
        var result=mvc.perform(get("/customer/orders").with(user("buyer@fe052.invalid").roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Bạn chưa có đơn hàng nào"))).andReturn();
        save("empty-history",result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(get("/customer/orders").with(user("buyer@fe052.invalid").roles("CUSTOMER")).param("keyword","NOT-FOUND"))
                .andExpect(content().string(containsString("Chưa có đơn hàng phù hợp")));
        when(lifecycle.history(eq(7L),any())).thenReturn(new PageImpl<>(rows(),PageRequest.of(2,10),21));
        mvc.perform(get("/customer/orders").with(user("buyer@fe052.invalid").roles("CUSTOMER")).param("page","1000"))
                .andExpect(content().string(containsString("Trang yêu cầu không còn tồn tại. Đang hiển thị trang 3.")));
    }
    @Test void foreignMissingOrdersAndConfirmationReturnSame404() throws Exception {
        when(details.getDetails(eq(7L),anyLong())).thenThrow(new ResourceNotFoundException("Không tìm thấy đơn hàng của bạn."));
        for(String path:List.of("/customer/orders/999","/customer/orders/999/confirmation","/customer/orders/0"))
            mvc.perform(get(path).with(user("buyer@fe052.invalid").roles("CUSTOMER")).param("customerId","999"))
                    .andExpect(status().isNotFound()).andExpect(view().name("customer/order-not-found"));
        verify(details).getDetails(7L,0L); verifyNoInteractions(lifecycle);
    }
    @Test void cancelledInvoiceShowsRefundActualPaymentsAndSnapshotTotals() throws Exception {
        var payments=List.of(new OrderDetailsResponse.PaymentInfo(13L,"Chuyển khoản ngân hàng","BANK_TRANSFER","REFUNDED","Đã hoàn tiền",new BigDecimal("59000"),LocalDateTime.of(2026,10,11,0,0)),
                new OrderDetailsResponse.PaymentInfo(12L,"Thanh toán trực tuyến","ONLINE","FAILED","Thanh toán thất bại",new BigDecimal("59000"),null));
        when(details.getDetails(7L,1L)).thenReturn(invoice("Đã hủy","Đã hoàn tiền",payments));
        var result=mvc.perform(get("/customer/orders/1").with(user("buyer@fe052.invalid").roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Đã hoàn tiền")))
                .andExpect(content().string(containsString("Thanh toán thất bại"))).andExpect(content().string(containsString("SKU:")))
                .andExpect(content().string(containsString("Tổng đơn cửa hàng"))).andExpect(content().string(containsString("1.000,50"))).andReturn();
        save("detail-refunded",result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }
    @Test void codPendingIsNeverShownAsCollectedAndMissingPaymentHasNoCodFallback() throws Exception {
        var payments=List.of(new OrderDetailsResponse.PaymentInfo(13L,"Thanh toán khi nhận hàng (COD)","COD","PENDING","Chờ thanh toán",new BigDecimal("59000"),null));
        when(details.getDetails(7L,1L)).thenReturn(invoice("Chờ cửa hàng xác nhận","Chưa thu tiền",payments));
        var result=mvc.perform(get("/customer/orders/1").with(user("buyer@fe052.invalid").roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Chưa thu tiền")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Đã thanh toán")))).andReturn();
        save("detail-cod",result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        when(details.getDetails(7L,1L)).thenReturn(invoice("Chờ cửa hàng xác nhận","Chưa thu tiền",List.of()));
        mvc.perform(get("/customer/orders/1").with(user("buyer@fe052.invalid").roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Chưa có bản ghi thanh toán.")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Thanh toán khi nhận hàng (COD)"))));
    }

    @Test void multiShopDetailRendersEachStoredShopTotalAndOrderTotal() throws Exception {
        var base=invoice("Đang xử lý","Chưa thu tiền",List.of());
        var item=new OrderDetailsResponse.Item("Xoài snapshot","Hộp snapshot","SNAP-XOAI","/images/buoi-da-xanh.jpg",1,new BigDecimal("50000"),new BigDecimal("50000"));
        var second=new OrderDetailsResponse.ShopGroup(13L,"Cửa hàng thứ hai","Sẵn sàng giao",new BigDecimal("25000"),new BigDecimal("75000"),List.of(item),new BigDecimal("50000"),BigDecimal.ZERO);
        var result=new OrderDetailsResponse(base.orderId(),base.orderCode(),base.statusLabel(),base.paymentStatusLabel(),base.recipientName(),base.phone(),base.addressText(),
                new BigDecimal("90000.50"),new BigDecimal("1000.50"),new BigDecimal("45000"),new BigDecimal("134000"),base.placedAt(),List.of(base.shops().getFirst(),second),List.of());
        when(details.getDetails(7L,1L)).thenReturn(result);
        var response=mvc.perform(get("/customer/orders/1").with(user("buyer@fe052.invalid").roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Cửa hàng thứ hai")))
                .andExpect(content().string(containsString("75.000 ₫"))).andExpect(content().string(containsString("134.000 ₫")))
                .andExpect(content().string(containsString("SNAP-XOAI"))).andReturn();
        save("detail-multishop",response.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }
}
