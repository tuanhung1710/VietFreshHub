package VietFreshHub;

import VietFreshHub.auth.entity.User;
import VietFreshHub.auth.repository.UserRepository;
import VietFreshHub.auth.repository.UserRoleRepository;
import VietFreshHub.admin.dto.AdminDashboardData;
import VietFreshHub.admin.service.AdminDashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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

@SpringBootTest
@AutoConfigureMockMvc
class VietFreshHubApplicationTests {
	@Autowired MockMvc mvc;
	@MockitoBean UserRepository userRepository;
	@MockitoBean UserRoleRepository userRoleRepository;
	@MockitoBean AdminDashboardService adminDashboardService;

	@Test
	void contextLoads() {
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
