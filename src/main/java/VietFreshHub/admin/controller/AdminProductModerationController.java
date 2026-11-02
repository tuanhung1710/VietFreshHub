package VietFreshHub.admin.controller;

import VietFreshHub.admin.service.AdminProductModerationService;
import VietFreshHub.auth.service.AuthService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/products")
public class AdminProductModerationController {

    private final AdminProductModerationService moderationService;
    private final AuthService authService;

    @GetMapping
    public String list(
            @RequestParam(defaultValue = "PENDING") String tab,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "ALL") String dateRange,
            @RequestParam(defaultValue = "1") int page,
            Model model) {
        String selectedDateRange = normalizeDateRange(dateRange);
        Page<?> products = moderationService.search(tab, keyword, selectedDateRange, page);
        int pageNumber = products.getNumber() + 1;
        int pageCount = Math.max(products.getTotalPages(), 1);
        int firstVisiblePage = Math.max(1, Math.min(pageNumber - 2, pageCount - 4));
        int lastVisiblePage = Math.min(pageCount, firstVisiblePage + 4);
        model.addAttribute("productsPage", products);
        model.addAttribute("products", products.getContent());
        model.addAttribute("tab", normalizeTab(tab));
        model.addAttribute("keyword", keyword == null ? "" : keyword);
        model.addAttribute("dateRange", selectedDateRange);
        model.addAttribute("pageNumber", pageNumber);
        model.addAttribute("pageCount", pageCount);
        model.addAttribute("firstVisiblePage", firstVisiblePage);
        model.addAttribute("lastVisiblePage", lastVisiblePage);
        model.addAttribute("pageNumbers", java.util.stream.IntStream
                .rangeClosed(firstVisiblePage, lastVisiblePage)
                .boxed()
                .toList());
        model.addAttribute("pendingCount", moderationService.countPending());
        model.addAttribute("reopenCount", moderationService.countReopen());
        model.addAttribute("lockedCount", moderationService.countLocked());
        model.addAttribute("reviewedCount", moderationService.countReviewed());
        return "admin/product-moderation";
    }

    @GetMapping("/{productId}")
    public String detail(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "PENDING") String tab,
            Model model,
            RedirectAttributes redirectAttributes) {
        String selectedTab = normalizeTab(tab);
        try {
            model.addAttribute("detail", moderationService.getDetail(productId));
            model.addAttribute("tab", selectedTab);
            return "admin/product-moderation-detail";
        } catch (EntityNotFoundException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
            redirectAttributes.addAttribute("tab", selectedTab);
            return "redirect:/admin/products";
        }
    }

    @PostMapping("/{productId}/review")
    public String review(
            @PathVariable Long productId,
            @RequestParam String action,
            @RequestParam(required = false) String reason,
            @RequestParam(defaultValue = "PENDING") String tab,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "ALL") String dateRange,
            @RequestParam(defaultValue = "1") int page,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        String selectedTab = normalizeTab(tab);
        try {
            moderationService.review(productId, action, reason,
                    authService.getCurrentUserId(authentication));
            redirectAttributes.addFlashAttribute("successMessage", successMessage(action));
        } catch (IllegalArgumentException | IllegalStateException | EntityNotFoundException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        redirectAttributes.addAttribute("tab", selectedTab);
        redirectAttributes.addAttribute("keyword", keyword == null ? "" : keyword);
        redirectAttributes.addAttribute("dateRange", normalizeDateRange(dateRange));
        redirectAttributes.addAttribute("page", Math.max(page, 1));
        return "redirect:/admin/products";
    }

    @PostMapping("/bulk-review")
    public String bulkReview(
            @RequestParam(defaultValue = "PENDING") String tab,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "ALL") String dateRange,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        String selectedTab = normalizeTab(tab);
        String selectedDateRange = normalizeDateRange(dateRange);
        try {
            if (!"PENDING".equals(selectedTab)) {
                throw new IllegalArgumentException("Chỉ có thể duyệt hàng loạt trong danh sách chưa hậu kiểm.");
            }
            int approvedCount = moderationService.bulkApprovePending(
                    keyword, selectedDateRange, authService.getCurrentUserId(authentication));
            if (approvedCount == 0) {
                redirectAttributes.addFlashAttribute("errorMessage", "Không còn sản phẩm phù hợp để duyệt.");
            } else {
                redirectAttributes.addFlashAttribute("successMessage",
                        "Đã hậu kiểm và duyệt " + approvedCount + " sản phẩm.");
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        redirectAttributes.addAttribute("tab", selectedTab);
        redirectAttributes.addAttribute("keyword", keyword == null ? "" : keyword);
        redirectAttributes.addAttribute("dateRange", selectedDateRange);
        redirectAttributes.addAttribute("page", 1);
        return "redirect:/admin/products";
    }

    private String normalizeTab(String tab) {
        if (tab == null) return "PENDING";
        String normalized = tab.trim().toUpperCase(java.util.Locale.ROOT);
        return java.util.Set.of("PENDING", "REOPEN", "LOCKED", "REVIEWED").contains(normalized)
                ? normalized : "PENDING";
    }

    private String normalizeDateRange(String dateRange) {
        if (dateRange == null || dateRange.isBlank()) return "ALL";
        String normalized = dateRange.trim().toUpperCase(java.util.Locale.ROOT);
        return java.util.Set.of("ALL", "TODAY", "LAST_3_DAYS", "LAST_7_DAYS", "LAST_30_DAYS")
                .contains(normalized) ? normalized : "ALL";
    }

    private String successMessage(String action) {
        if ("BLOCK".equalsIgnoreCase(action)) return "Đã tạm khóa sản phẩm.";
        if ("UNBLOCK".equalsIgnoreCase(action)) return "Đã mở khóa và duyệt lại sản phẩm.";
        return "Đã hậu kiểm và duyệt sản phẩm.";
    }
}
