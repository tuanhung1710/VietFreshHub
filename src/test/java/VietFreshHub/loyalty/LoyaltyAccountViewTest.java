package VietFreshHub.loyalty;

import VietFreshHub.auth.entity.User;
import VietFreshHub.auth.repository.UserRepository;
import VietFreshHub.auth.service.CurrentCustomerService;
import VietFreshHub.loyalty.controller.LoyaltyController;
import VietFreshHub.loyalty.entity.LoyaltyAccount;
import VietFreshHub.loyalty.entity.LoyaltyTransaction;
import VietFreshHub.loyalty.entity.LoyaltyTransactionType;
import VietFreshHub.loyalty.repository.LoyaltyAccountRepository;
import VietFreshHub.loyalty.repository.LoyaltyTransactionRepository;
import VietFreshHub.loyalty.services.LoyaltyServiceImpl;
import VietFreshHub.order.entity.Order;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LoyaltyAccountViewTest {
    private final LoyaltyAccountRepository accounts = mock(LoyaltyAccountRepository.class);
    private final LoyaltyTransactionRepository transactions = mock(LoyaltyTransactionRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final UsernamePasswordAuthenticationToken customer = UsernamePasswordAuthenticationToken.authenticated(
            "customer@example.com", "", AuthorityUtils.createAuthorityList("ROLE_CUSTOMER"));
    private MockMvc mvc;
    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setUserId(7L);
        user.setStatus("ACTIVE");
        when(users.findByEmailIgnoreCase("customer@example.com")).thenReturn(Optional.of(user));
        when(accounts.findByUser_UserId(7L)).thenReturn(Optional.empty());
        when(transactions.findByLoyaltyAccount_User_UserIdOrderByCreatedAtDescLoyaltyTransactionIdDesc(
                eq(7L), any(Pageable.class))).thenAnswer(invocation ->
                new PageImpl<LoyaltyTransaction>(List.of(), invocation.getArgument(1), 0));

        var templates = new ClassLoaderTemplateResolver();
        templates.setPrefix("templates/");
        templates.setSuffix(".html");
        templates.setTemplateMode("HTML");
        templates.setCharacterEncoding(StandardCharsets.UTF_8.name());
        var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(templates);
        var views = new ThymeleafViewResolver();
        views.setTemplateEngine(engine);
        views.setCharacterEncoding(StandardCharsets.UTF_8.name());
        var controller = new LoyaltyController(new LoyaltyServiceImpl(accounts, transactions,
                mock(VietFreshHub.loyalty.repository.LoyaltyRuleRepository.class),
                mock(jakarta.persistence.EntityManager.class)),
                new CurrentCustomerService(users));
        mvc = MockMvcBuilders.standaloneSetup(controller).setViewResolvers(views).build();
    }

    @Test
    void rendersBalanceTotalsAndTimestampForAuthenticatedCustomerOnly() throws Exception {
        when(accounts.findByUser_UserId(7L)).thenReturn(Optional.of(LoyaltyAccount.builder()
                .loyaltyAccountId(1L).user(user).rewardBalance(new BigDecimal("1250")).totalEarnedAmount(new BigDecimal("2000")).totalRedeemedAmount(new BigDecimal("750"))
                .updatedAt(LocalDateTime.of(2026, 10, 18, 10, 20, 30)).build()));
        mvc.perform(get("/customer/loyalty").principal(customer).param("userId", "999"))
                .andExpect(status().isOk()).andExpect(view().name("loyalty/loyalty-account"))
                .andExpect(content().string(containsString(">1.250</p>")))
                .andExpect(content().string(containsString(">2.000</p>")))
                .andExpect(content().string(containsString(">750</p>")))
                .andExpect(content().string(containsString("18/10/2026 10:20:30")));
        verify(accounts).findByUser_UserId(7L);
        verify(accounts, never()).findByUser_UserId(999L);
        verify(transactions).findByLoyaltyAccount_User_UserIdOrderByCreatedAtDescLoyaltyTransactionIdDesc(
                7L, PageRequest.of(0, 10));
    }

    @Test
    void customerWithoutAccountSeesZeroAndEmptyHistoryWithoutCreatingAccount() throws Exception {
        mvc.perform(get("/customer/loyalty").principal(customer))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(">0</p>")))
                .andExpect(content().string(containsString("Bạn chưa có điểm thưởng")))
                .andExpect(content().string(containsString("Chưa có giao dịch điểm thưởng.")))
                .andExpect(content().string(not(containsString("aria-label=\"Phân trang giao dịch điểm\""))));
        verify(accounts, never()).save(any());
    }

    @Test
    void rendersAllTransactionTypesOrderLinksSignedPointsAndEscapedNotes() throws Exception {
        var order = new Order();
        order.setOrderId(42L);
        order.setOrderCode("VF-42");
        var entries = List.of(
                transaction(1L, LoyaltyTransactionType.EARN, 100, 100, order, "Tích từ đơn hàng"),
                transaction(2L, LoyaltyTransactionType.REDEEM, -30, 70, order, null),
                transaction(3L, LoyaltyTransactionType.REFUND, 30, 100, order, "Hoàn điểm"),
                transaction(4L, LoyaltyTransactionType.ADJUSTMENT, -10, 90, null, "<script>alert(1)</script>"));
        when(transactions.findByLoyaltyAccount_User_UserIdOrderByCreatedAtDescLoyaltyTransactionIdDesc(
                7L, PageRequest.of(0, 10))).thenReturn(new PageImpl<>(entries));
        mvc.perform(get("/customer/loyalty").principal(customer))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tích điểm")))
                .andExpect(content().string(containsString("Đổi điểm")))
                .andExpect(content().string(containsString("Hoàn điểm")))
                .andExpect(content().string(containsString("Điều chỉnh")))
                .andExpect(content().string(containsString("href=\"/customer/orders/42\"")))
                .andExpect(content().string(containsString("VF-42")))
                .andExpect(content().string(containsString(">-30</td>")))
                .andExpect(content().string(containsString(">70</td>")))
                .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
                .andExpect(content().string(not(containsString("href=\"/customer/orders/null\""))));
    }

    @Test
    void paginationRendersFirstMiddleAndLastPages() throws Exception {
        when(transactions.findByLoyaltyAccount_User_UserIdOrderByCreatedAtDescLoyaltyTransactionIdDesc(
                eq(7L), any(Pageable.class))).thenAnswer(invocation -> new PageImpl<>(
                Collections.nCopies(10, transaction(1L, LoyaltyTransactionType.EARN, 1, 1, null, null)),
                invocation.getArgument(1), 30));
        for (int page = 0; page < 3; page++) {
            var result = mvc.perform(get("/customer/loyalty").principal(customer).param("page", "" + page))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Trang " + (page + 1) + " / 3")))
                    .andReturn();
            String html = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertEquals(page > 0, html.contains(">Trước</a>"));
            assertEquals(page < 2, html.contains(">Sau</a>"));
            if (page > 0) assertTrue(html.contains("/customer/loyalty?page=" + (page - 1)));
            if (page < 2) assertTrue(html.contains("/customer/loyalty?page=" + (page + 1)));
        }
    }

    @Test
    void negativePageUsesFirstPage() throws Exception {
        mvc.perform(get("/customer/loyalty").principal(customer).param("page", "-5"))
                .andExpect(status().isOk());
        verify(transactions).findByLoyaltyAccount_User_UserIdOrderByCreatedAtDescLoyaltyTransactionIdDesc(
                7L, PageRequest.of(0, 10));
    }

    @Test
    void missingAuthenticationCannotReadLoyaltyData() {
        var exception = assertThrows(ServletException.class,
                () -> mvc.perform(get("/customer/loyalty")));
        assertInstanceOf(AccessDeniedException.class, exception.getCause());
        verifyNoInteractions(accounts, transactions);
    }

    @Test
    void nonCustomerCannotReadLoyaltyData() {
        var seller = UsernamePasswordAuthenticationToken.authenticated("seller@example.com", "",
                AuthorityUtils.createAuthorityList("ROLE_STORE_MANAGER"));
        var exception = assertThrows(ServletException.class,
                () -> mvc.perform(get("/customer/loyalty").principal(seller)));
        assertInstanceOf(AccessDeniedException.class, exception.getCause());
        verifyNoInteractions(accounts, transactions);
    }

    private LoyaltyTransaction transaction(Long id, LoyaltyTransactionType type, int reward,
                                           int after, Order order, String note) {
        return LoyaltyTransaction.builder().loyaltyTransactionId(id).order(order)
                .transactionType(type).amount(BigDecimal.valueOf(reward)).balanceAfterAmount(BigDecimal.valueOf(after)).note(note)
                .createdAt(LocalDateTime.of(2026, 10, 18, 10, 0)).build();
    }
}
