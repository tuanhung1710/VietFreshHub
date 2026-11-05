package VietFreshHub.payment.vnpay;

import VietFreshHub.subscription.services.ShopSubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.mock;

class SubscriptionVnpayControllerTest {
    private final ShopSubscriptionService subscriptions = mock(ShopSubscriptionService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new SubscriptionVnpayController(subscriptions)).build();
    }

    @Test
    void ipnAcceptsGetAndReturnsVnpayJson() throws Exception {
        when(subscriptions.handleVnpayCallback(Map.of("vnp_TxnRef", "VFS41", "vnp_ResponseCode", "00")))
                .thenReturn("00|Confirmed");

        mvc.perform(get("/payment/vnpay/subscription/ipn")
                        .param("vnp_TxnRef", "VFS41")
                        .param("vnp_ResponseCode", "00"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.RspCode").value("00"))
                .andExpect(jsonPath("$.Message").value("Confirmed"));

        verify(subscriptions).handleVnpayCallback(Map.of("vnp_TxnRef", "VFS41", "vnp_ResponseCode", "00"));
    }

    @Test
    void ipnRejectsPost() throws Exception {
        mvc.perform(post("/payment/vnpay/subscription/ipn"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void returnUrlAcceptsGetAndDelegatesCallbackVerification() throws Exception {
        when(subscriptions.handleVnpayCallback(Map.of("vnp_TxnRef", "VFS41"))).thenReturn("00|Confirmed");

        mvc.perform(get("/payment/vnpay/subscription/return").param("vnp_TxnRef", "VFS41"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seller/subscription/payments"));

        verify(subscriptions).handleVnpayCallback(Map.of("vnp_TxnRef", "VFS41"));
    }
}
