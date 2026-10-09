package VietFreshHub.admin.controller;

import VietFreshHub.admin.dto.AdminAccountRow;
import VietFreshHub.admin.service.AdminAccountService;
import VietFreshHub.auth.entity.Role;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.List;
import java.util.Set;

@Controller
@RequestMapping("/admin")
public class AdminAccountController {

    private final AdminAccountService service;

    public AdminAccountController(AdminAccountService service) {
        this.service = service;
    }

    @GetMapping("/accounts")
    public String showAccounts(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(defaultValue = "ALL") String role,
            @RequestParam(defaultValue = "1") int page,
            Model model
    ) {
        Page<AdminAccountRow> accounts = service.getAccounts(keyword, status, role, page);
        List<Role> availableRoles = service.getRoles();
        model.addAttribute("accountsPage", accounts);
        model.addAttribute("accounts", accounts.getContent());
        model.addAttribute("stats", service.getStats());
        model.addAttribute("roles", availableRoles);
        model.addAttribute("keyword", keyword);
        model.addAttribute("selectedStatus", normalizeStatusFilter(status));
        model.addAttribute("selectedRole", normalizeRoleFilter(role, availableRoles.stream()
                .map(Role::getRoleName).collect(java.util.stream.Collectors.toSet())));
        model.addAttribute("pageNumber", accounts.getNumber() + 1);
        model.addAttribute("pageCount", Math.max(accounts.getTotalPages(), 1));
        return "admin/accounts";
    }

    @GetMapping("/accounts-demo")
    public String redirectLegacyAccountsUrl() {
        return "redirect:/admin/accounts";
    }

    @PostMapping("/accounts/{userId}/profile")
    public String updateProfile(
            @PathVariable Long userId,
            @RequestParam String fullName,
            @RequestParam(required = false) String phone,
            RedirectAttributes redirectAttributes
    ) {
        return runUpdate(redirectAttributes,
                () -> service.updateProfile(userId, fullName, phone),
                "Đã cập nhật hồ sơ tài khoản.");
    }

    @PostMapping("/accounts/{userId}/roles")
    public String updateRoles(
            @PathVariable Long userId,
            @RequestParam(required = false) List<Integer> roleIds,
            RedirectAttributes redirectAttributes
    ) {
        return runUpdate(redirectAttributes,
                () -> service.updateRoles(userId, roleIds),
                "Đã cập nhật role tài khoản.");
    }

    @PostMapping("/accounts/{userId}/status")
    public String updateStatus(
            @PathVariable Long userId,
            @RequestParam String status,
            RedirectAttributes redirectAttributes
    ) {
        return runUpdate(redirectAttributes,
                () -> service.updateStatus(userId, status),
                "Đã cập nhật trạng thái tài khoản.");
    }

    private String runUpdate(
            RedirectAttributes redirectAttributes,
            Runnable action,
            String successMessage
    ) {
        try {
            action.run();
            redirectAttributes.addFlashAttribute("successMessage", successMessage);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/admin/accounts";
    }

    private String normalizeStatusFilter(String status) {
        if (status == null || status.isBlank()) return "ALL";
        String normalized = status.trim().toUpperCase(java.util.Locale.ROOT);
        return Set.of("ALL", "ACTIVE", "INACTIVE", "BLOCKED", "DELETED").contains(normalized)
                ? normalized : "ALL";
    }

    private String normalizeRoleFilter(String role, Set<String> knownRoles) {
        return role == null || role.isBlank() || "ALL".equalsIgnoreCase(role) || !knownRoles.contains(role)
                ? "ALL" : role;
    }
}
