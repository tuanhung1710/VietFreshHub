package VietFreshHub.customer;

import VietFreshHub.auth.controller.AuthController;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class HomePaginationRedirectTest {
    @Test void preservesPageAndSearchWhenRedirectingToCustomerCatalog() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new AuthController(null, null, null)).build();
        mvc.perform(get("/home").param("page", "2").param("keyword", "xoai"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/customer/home?page=2&keyword=xoai"));
        mvc.perform(get("/home").param("page", "2").param("keyword", "tao").param("category", "trai-cay-nhap-khau"))
                .andExpect(redirectedUrl("/customer/home?page=2&category=trai-cay-nhap-khau&keyword=tao"));
        mvc.perform(get("/home").param("page", "2"))
                .andExpect(redirectedUrl("/customer/home?page=2"));
    }
}
