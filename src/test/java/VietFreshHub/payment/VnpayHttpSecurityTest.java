package VietFreshHub.payment;

import VietFreshHub.auth.entity.User;
import VietFreshHub.auth.repository.*;
import VietFreshHub.cart.service.CartService;
import VietFreshHub.catalog.service.CatalogService;
import VietFreshHub.checkout.service.*;
import VietFreshHub.customer.CustomerHttpHarness;
import VietFreshHub.customer.service.CustomerAddressService;
import VietFreshHub.order.service.*;
import VietFreshHub.payment.service.*;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

class VnpayHttpSecurityTest {
    final VnpayPaymentService online=mock(VnpayPaymentService.class);
    final UserRepository users=mock(UserRepository.class);
    final UserRoleRepository roles=mock(UserRoleRepository.class);
    CustomerHttpHarness harness() {
        User user=new User(); user.setUserId(7L); user.setStatus("ACTIVE");
        when(users.findByEmailIgnoreCase("buyer@test.invalid")).thenReturn(Optional.of(user));
        when(roles.findRoleNamesByUserId(7L)).thenReturn(List.of("ROLE_CUSTOMER"));
        return new CustomerHttpHarness(users,roles,mock(CartService.class),mock(CatalogService.class),mock(CheckoutService.class),
                mock(CheckoutShippingQuoteService.class),new CheckoutDraftStore(),mock(CustomerAddressService.class),
                mock(CustomerOrderService.class),mock(CustomerOrderQueryService.class),mock(CustomerPaymentHistoryService.class),online);
    }
    @Test void anonymousGetIpnIsPublicAndReturnsProviderJsonWithoutCsrf() throws Exception {
        when(online.receiveIpn(anyMap())).thenReturn(Map.of("RspCode","97","Message","Invalid signature"));
        try(var http=harness()) {
            http.mvc.perform(get("/payments/vnpay/ipn").param("vnp_TxnRef","A","B"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.RspCode").value("97"))
                    .andExpect(header().string("Cache-Control","no-store"));
            verify(online).receiveIpn(argThat(p -> p.get("vnp_TxnRef").size()==2));
        }
    }
    @Test void callbackCommitFailureRequestsProviderRetry() throws Exception {
        when(online.receiveIpn(anyMap())).thenThrow(new org.springframework.transaction.TransactionSystemException("test"));
        try(var http=harness()) {
            http.mvc.perform(get("/payments/vnpay/ipn")).andExpect(status().isOk()).andExpect(jsonPath("$.RspCode").value("99"));
        }
    }
    @Test void returnIsPublicAndCannotWritePayment() throws Exception {
        try(var http=harness()) {
            http.mvc.perform(get("/payments/vnpay/return"))
                    .andExpect(status().isOk()).andExpect(view().name("customer/vnpay-return"));
            verify(online,never()).receiveIpn(anyMap());
        }
    }
    @Test void customerPaymentRoutesRequireLoginAndPostCsrf() throws Exception {
        try(var http=harness()) {
            http.mvc.perform(get("/customer/payments/orders/1")).andExpect(status().is3xxRedirection());
            http.mvc.perform(post("/customer/payments/orders/1/pay").with(user("buyer@test.invalid").roles("CUSTOMER")))
                    .andExpect(status().isForbidden());
            verifyNoInteractions(online);
        }
    }
    @Test void paymentStartUsesAuthenticatedCustomerAndRemoteAddress() throws Exception {
        when(online.start(7L,1L,"127.0.0.1")).thenReturn(URI.create("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?test=1"));
        try(var http=harness()) {
            http.mvc.perform(post("/customer/payments/orders/1/pay").with(user("buyer@test.invalid").roles("CUSTOMER")).with(csrf())
                    .param("customerId","999").header("X-Forwarded-For","8.8.8.8"))
                    .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?test=1"));
        }
    }
    @Test void callbacksRejectPostEvenWithCsrf() throws Exception {
        try(var http=harness()) {
            http.mvc.perform(post("/payments/vnpay/ipn").with(csrf())).andExpect(status().isForbidden());
            verifyNoInteractions(online);
        }
    }
}
