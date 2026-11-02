package VietFreshHub.order.controller;

import VietFreshHub.auth.service.CurrentCustomerService;
import VietFreshHub.exception.ResourceNotFoundException;
import VietFreshHub.order.dto.OrderDetailsResponse;
import VietFreshHub.order.repository.CustomerOrderReadRepository;
import VietFreshHub.order.repository.OrderAddressRepository;
import VietFreshHub.order.repository.OrderRepository;
import VietFreshHub.order.service.CustomerOrderQueryService;
import VietFreshHub.order.service.CustomerOrderService;
import VietFreshHub.order.service.impl.CustomerOrderQueryServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CustomerOrderDetailsControllerTest {
    @Test
    void customerCanOpenExistingOrderDetailRoute() throws Exception {
        CurrentCustomerService currentCustomer = mock(CurrentCustomerService.class);
        CustomerOrderQueryService orders = mock(CustomerOrderQueryService.class);
        CustomerOrderService lifecycle = mock(CustomerOrderService.class);
        Authentication authentication = mock(Authentication.class);
        OrderDetailsResponse order = order(42L);
        when(currentCustomer.requireCustomerId(authentication)).thenReturn(7L);
        when(orders.getDetails(7L, 42L)).thenReturn(order);
        when(orders.getCartBadgeTotal(7L)).thenReturn(0L);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new CustomerOrderDetailsController(currentCustomer, orders, lifecycle)).build();

        mvc.perform(get("/customer/order-receipts/42").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/order-detail"))
                .andExpect(model().attribute("order", order));

        verify(orders).getDetails(7L, 42L);
    }

    @Test
    void customerCannotOpenAnotherCustomersOrder() throws Exception {
        CurrentCustomerService currentCustomer = mock(CurrentCustomerService.class);
        Authentication authentication = mock(Authentication.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        when(currentCustomer.requireCustomerId(authentication)).thenReturn(7L);
        when(orderRepository.findByOrderIdAndCustomerUserId(42L, 7L)).thenReturn(Optional.empty());
        CustomerOrderQueryService orders = new CustomerOrderQueryServiceImpl(orderRepository,
                mock(OrderAddressRepository.class), mock(CustomerOrderReadRepository.class));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new CustomerOrderDetailsController(
                currentCustomer, orders, mock(CustomerOrderService.class))).build();

        mvc.perform(get("/customer/order-receipts/42").principal(authentication))
                .andExpect(status().isNotFound())
                .andExpect(view().name("customer/order-not-found"));

        verify(orderRepository).findByOrderIdAndCustomerUserId(42L, 7L);
    }

    private OrderDetailsResponse order(Long orderId) {
        return new OrderDetailsResponse(orderId, "VF-42", "Chờ xác nhận", "Chờ thu COD",
                "Customer", "0900000000", "Address", BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, LocalDateTime.now(), List.of(), List.of());
    }
}
