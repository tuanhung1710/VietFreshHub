package VietFreshHub;

import VietFreshHub.auth.entity.User;
import VietFreshHub.auth.repository.UserRepository;
import VietFreshHub.auth.repository.UserRoleRepository;
import VietFreshHub.admin.dto.AdminDashboardData;
import VietFreshHub.admin.service.AdminDashboardService;
import VietFreshHub.inventory.dto.InventoryAttentionSummary;
import VietFreshHub.order.dto.ShopReportSummary;
import VietFreshHub.order.service.OrderService;
import VietFreshHub.product.service.ProductService;
import VietFreshHub.review.repository.ReviewRepository;
import VietFreshHub.review.service.ReviewService;
import VietFreshHub.shop.service.ShopService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Page;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:application-tests;MODE=MSSQLServer;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.properties.hibernate.hbm2ddl.auto=none", "inventory.alerts.enabled=false",
        "spring.security.oauth2.client.registration.google.client-id=test-client",
        "spring.security.oauth2.client.registration.google.client-secret=test-secret"
})
@AutoConfigureMockMvc
class VietFreshHubApplicationTests {
	@Autowired MockMvc mvc;
	@MockitoBean ReviewService reviewService;
	@MockitoBean OrderService orderService;
	@MockitoBean ShopService shopService;
	@MockitoBean ProductService productService;
	@MockitoBean UserRepository userRepository;
	@MockitoBean UserRoleRepository userRoleRepository;
	@MockitoBean AdminDashboardService adminDashboardService;
	@MockitoBean VietFreshHub.notification.service.NotificationService notificationService;

	@Test
	void contextLoads() {
	}

	@Test
	void approvedReviewsArePubliclyViewableAndAdminModerationIsRoleRestricted() throws Exception {
		when(reviewService.productReviews(1L, 0)).thenReturn(Page.empty());
		when(reviewService.productRating(1L)).thenReturn(org.mockito.Mockito.mock(ReviewRepository.RatingSummary.class));
		when(reviewService.pendingReviews(0)).thenReturn(Page.empty());
		stubActor("seller@test", 11L, "ROLE_STORE_MANAGER");
		stubActor("admin@test", 12L, "ROLE_ADMIN");

		mvc.perform(get("/reviews/product/1"))
				.andExpect(status().isOk()).andExpect(view().name("review/list"));
		mvc.perform(get("/admin/reviews/pending").with(SecurityMockMvcRequestPostProcessors.user("seller@test").roles("STORE_MANAGER")))
				.andExpect(status().isForbidden());
		mvc.perform(get("/admin/reviews/pending").with(SecurityMockMvcRequestPostProcessors.user("admin@test").roles("ADMIN")))
				.andExpect(status().isOk()).andExpect(view().name("admin/reviews-pending"));
	}

	@Test
	void sellerReportIsAvailableToStoreManagerButNotCustomer() throws Exception {
		var sales = new ShopReportSummary.Sales(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0L);
		LocalDate today = LocalDate.now();
		when(orderService.getShopReport(any(), isNull(), isNull()))
				.thenReturn(new ShopReportSummary(today, today, sales, List.of(), 0L, List.of()));
		when(productService.inventoryAttentionSummary(any())).thenReturn(new InventoryAttentionSummary(0, 0, 0, 1));
		stubActor("seller@test", 11L, "ROLE_STORE_MANAGER");
		stubActor("customer@test", 13L, "ROLE_CUSTOMER");

		mvc.perform(get("/seller/reports").with(SecurityMockMvcRequestPostProcessors.user("seller@test").roles("STORE_MANAGER")))
				.andExpect(status().isOk()).andExpect(view().name("store_manager/reports"));
		mvc.perform(get("/seller/reports").with(SecurityMockMvcRequestPostProcessors.user("customer@test").roles("CUSTOMER")))
				.andExpect(status().isForbidden());
	}

	@Test
	void adminDashboardIsVisibleOnlyToAdminAndRendersEmptyStates() throws Exception {
		LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
		when(adminDashboardService.getDashboard("LAST_30_DAYS", null, null, 1, 1)).thenReturn(
				new AdminDashboardData(today.minusDays(29), today, 0, 0, 0, BigDecimal.ZERO, 0,
						List.of(), 0, List.of(), 0, 0,
						java.util.Arrays.stream(VietFreshHub.order.entity.Order.Status.values())
								.map(status -> new AdminDashboardData.OrderStatusCount(status.name(), 0)).toList(),
						0, 0, 0, List.of(), 1, 0, List.of(), List.of(), List.of(), 0, 1, 0,
						0, List.of(), 0, 0, List.of()));
		stubActor("admin-dashboard@test", 14L, "ROLE_ADMIN");
		stubActor("manager-dashboard@test", 15L, "ROLE_STORE_MANAGER");
		stubActor("customer-dashboard@test", 16L, "ROLE_CUSTOMER");

		mvc.perform(get("/admin/dashboard").with(SecurityMockMvcRequestPostProcessors.user("admin-dashboard@test").roles("ADMIN")))
				.andExpect(status().isOk()).andExpect(view().name("admin/dashboard"))
				.andExpect(content().string(containsString("Admin Dashboard")))
				.andExpect(content().string(containsString("KhÃ´ng cÃ³ shop order giao thÃ nh cÃ´ng")));
		mvc.perform(get("/admin/dashboard").with(SecurityMockMvcRequestPostProcessors.user("manager-dashboard@test").roles("STORE_MANAGER")))
				.andExpect(status().isForbidden());
		mvc.perform(get("/admin/dashboard").with(SecurityMockMvcRequestPostProcessors.user("customer-dashboard@test").roles("CUSTOMER")))
				.andExpect(status().isForbidden());
		when(adminDashboardService.getDashboard("CUSTOM_RANGE", LocalDate.of(2026, 10, 10),
				LocalDate.of(2026, 10, 9), 1, 1)).thenThrow(new IllegalArgumentException("Vui lÃ²ng chá»n khoáº£ng ngÃ y há»£p lá»‡."));
		mvc.perform(get("/admin/dashboard")
				.param("period", "CUSTOM_RANGE").param("from", "2026-10-10").param("to", "2026-10-09")
				.with(SecurityMockMvcRequestPostProcessors.user("admin-dashboard@test").roles("ADMIN")))
				.andExpect(status().isOk()).andExpect(content().string(containsString("Vui lÃ²ng chá»n khoáº£ng ngÃ y há»£p lá»‡.")));
	}

	private void stubActor(String email, long userId, String role) {
		User user = new User();
		user.setUserId(userId);
		user.setEmail(email);
		user.setStatus("ACTIVE");
		when(userRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.of(user));
		when(userRoleRepository.findRoleNamesByUserId(userId)).thenReturn(List.of(role));
	}

}
