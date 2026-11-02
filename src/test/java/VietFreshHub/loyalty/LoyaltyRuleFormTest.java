package VietFreshHub.loyalty;

import VietFreshHub.loyalty.controller.LoyaltyRuleController;
import VietFreshHub.loyalty.entity.LoyaltyRule;
import VietFreshHub.loyalty.repository.LoyaltyRuleRepository;
import VietFreshHub.loyalty.services.LoyaltyRuleServiceImpl;
import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.math.BigDecimal;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LoyaltyRuleFormTest {
    private final LoyaltyRuleRepository rules = mock(LoyaltyRuleRepository.class);
    private final LoyaltyRule rule = new LoyaltyRule();
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        rule.setRuleId(1L); rule.setEarningPercent(new BigDecimal("1.00")); rule.setEarningEnabled(true);
        rule.setMinRedemptionAmount(new BigDecimal("1000.00")); rule.setMaxRedemptionPercent(20); rule.setRedemptionEnabled(true);
        when(rules.findById(1L)).thenReturn(Optional.of(rule));
        when(rules.findForUpdate(1L)).thenReturn(Optional.of(rule));
        when(rules.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        var resolver = new ClassLoaderTemplateResolver(); resolver.setPrefix("templates/"); resolver.setSuffix(".html"); resolver.setCharacterEncoding("UTF-8");
        var engine = new SpringTemplateEngine(); engine.setTemplateResolver(resolver);
        var views = new ThymeleafViewResolver(); views.setTemplateEngine(engine); views.setCharacterEncoding("UTF-8");
        var service = new LoyaltyRuleServiceImpl(rules, Validation.buildDefaultValidatorFactory().getValidator());
        mvc = MockMvcBuilders.standaloneSetup(new LoyaltyRuleController(service)).setViewResolvers(views).build();
    }

    @Test
    void rendersPercentAndVndInputsWithCorrectMeaning() throws Exception {
        mvc.perform(get("/admin/loyalty-rules/earning")).andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"earningPercent\"")))
                .andExpect(content().string(containsString("value=\"1.00\"")))
                .andExpect(content().string(containsString("Nhập 1.00 nghĩa là 1%")));
        mvc.perform(get("/admin/loyalty-rules/redemption")).andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"minRedemptionAmount\"")))
                .andExpect(content().string(containsString("1 ₫ tiền thưởng giảm đúng 1 ₫")));
    }

    @Test
    void savesPercentWithoutScalingAndSavesMoneyRules() throws Exception {
        mvc.perform(post("/admin/loyalty-rules/earning").param("earningPercent", "2.50").param("earningEnabled", "true"))
                .andExpect(status().is3xxRedirection());
        assertEquals(new BigDecimal("2.50"), rule.getEarningPercent());
        mvc.perform(post("/admin/loyalty-rules/redemption").param("minRedemptionAmount", "1500").param("maxRedemptionPercent", "15").param("redemptionEnabled", "true"))
                .andExpect(status().is3xxRedirection());
        assertEquals(new BigDecimal("1500"), rule.getMinRedemptionAmount());
        assertEquals(15, rule.getMaxRedemptionPercent());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "100.01", "1.001"})
    void rejectsInvalidPercent(String input) throws Exception {
        mvc.perform(post("/admin/loyalty-rules/earning").param("earningPercent", input))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("form", "earningPercent"));
        verify(rules, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1000.5"})
    void rejectsInvalidMinimumMoney(String input) throws Exception {
        mvc.perform(post("/admin/loyalty-rules/redemption").param("minRedemptionAmount", input).param("maxRedemptionPercent", "20"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("form", "minRedemptionAmount"));
        verify(rules, never()).saveAndFlush(any());
    }
}
