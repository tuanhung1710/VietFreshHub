package VietFreshHub.promotion;

import VietFreshHub.promotion.controller.PromotionController;
import VietFreshHub.promotion.dto.PromotionResponse;
import VietFreshHub.promotion.entity.*;
import VietFreshHub.promotion.services.PromotionService;
import VietFreshHub.shop.service.ShopService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.view.RedirectView;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PromotionControllerTest {
    private final PromotionService service = mock(PromotionService.class);
    private final ShopService shops = mock(ShopService.class);
    private final Authentication auth = mock(Authentication.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        when(shops.getManagedShopId(auth)).thenReturn(2L);
        mvc = MockMvcBuilders.standaloneSetup(new PromotionController(service, shops))
                .setViewResolvers((name, locale) -> name.startsWith("redirect:")
                        ? new RedirectView(name.substring("redirect:".length())) : mock(View.class)).build();
    }

    @Test
    void missingOrUnownedEditRedirectsWithExpectedMessage() throws Exception {
        when(service.getById(99L, 2L)).thenThrow(new IllegalArgumentException("Không tìm thấy chương trình thuộc shop này"));
        mvc.perform(get("/seller/promotions/99/edit").principal(auth))
                .andExpect(redirectedUrl("/seller/promotions"))
                .andExpect(flash().attribute("errorMessage", "Không tìm thấy chương trình thuộc shop này"));
    }

    @Test
    void invalidPostLookupUsesSameHandler() throws Exception {
        when(service.getById(99L, 2L)).thenThrow(new IllegalArgumentException("ID chương trình không hợp lệ"));
        mvc.perform(post("/seller/promotions/99/edit").principal(auth))
                .andExpect(redirectedUrl("/seller/promotions"))
                .andExpect(flash().attribute("errorMessage", "ID chương trình không hợp lệ"));
        verify(service, never()).update(anyLong(), anyLong(), any());
    }

    @Test
    void existingValidationKeepsSubmittedForm() throws Exception {
        mvc.perform(post("/seller/promotions").principal(auth).param("name", "Submitted name"))
                .andExpect(view().name("promotions/promotion-form"))
                .andExpect(model().attributeHasFieldErrors("promotionForm", "discountType", "startAt", "endAt"));
        verifyNoInteractions(service);
    }

    @Test
    void activationAlreadyHandlesExpectedStatusErrors() throws Exception {
        doThrow(new IllegalStateException("Không thể kích hoạt chương trình đã hủy hoặc hết hạn")).when(service).activate(1L, 2L);
        mvc.perform(post("/seller/promotions/1/activate").principal(auth))
                .andExpect(redirectedUrl("/seller/promotions"))
                .andExpect(flash().attribute("errorMessage", "Không thể kích hoạt chương trình đã hủy hoặc hết hạn"));
    }

    @Test
    void existingUpdateStatusRaceKeepsFormAndPromotionId() throws Exception {
        when(service.getById(1L, 2L)).thenReturn(PromotionResponse.from(Promotion.builder().promotionId(1L).shopId(2L)
                .status(PromotionStatus.INACTIVE).build()));
        doThrow(new IllegalStateException("Concurrent activation")).when(service).update(eq(1L), eq(2L), any());
        mvc.perform(post("/seller/promotions/1/edit").principal(auth).param("name", "Submitted")
                        .param("discountType", "PERCENT").param("discountValue", "10")
                        .param("startAt", "2030-01-01T08:00").param("endAt", "2030-01-02T08:00"))
                .andExpect(view().name("promotions/promotion-form"))
                .andExpect(model().attribute("promotionId", 1L)).andExpect(model().attributeHasErrors("promotionForm"));
    }

    @Test
    void databaseErrorsAreNotCaughtByBusinessHandler() {
        var failure = new DataIntegrityViolationException("database constraint");
        when(service.getById(1L, 2L)).thenThrow(failure);
        var thrown = assertThrows(jakarta.servlet.ServletException.class,
                () -> mvc.perform(get("/seller/promotions/1/edit").principal(auth)));
        assertSame(failure, thrown.getCause());
    }
}
