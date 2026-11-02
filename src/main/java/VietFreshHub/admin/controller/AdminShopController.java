package VietFreshHub.admin.controller;

import VietFreshHub.admin.dto.AdminShopRow;
import VietFreshHub.admin.service.AdminShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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
@RequestMapping("/admin/shops")
public class AdminShopController {

    private final AdminShopService shopService;

    @GetMapping
    public String list(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(defaultValue = "1") int page,
            Model model
    ) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        String selectedStatus = shopService.normalizeFilterStatus(status);
        Page<AdminShopRow> shopsPage = shopService.search(normalizedKeyword, selectedStatus, page);

        model.addAttribute("shopsPage", shopsPage);
        model.addAttribute("shops", shopsPage.getContent());
        model.addAttribute("keyword", normalizedKeyword);
        model.addAttribute("selectedStatus", selectedStatus);
        model.addAttribute("pageNumber", shopsPage.getNumber() + 1);
        model.addAttribute("pageCount", Math.max(shopsPage.getTotalPages(), 1));
        return "admin/shops";
    }

    @PostMapping("/{shopId}/status")
    public String changeStatus(
            @PathVariable Long shopId,
            @RequestParam String status,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "ALL") String selectedStatus,
            @RequestParam(defaultValue = "1") int page,
            RedirectAttributes redirectAttributes
    ) {
        try {
            shopService.changeStatus(shopId, status);
            redirectAttributes.addFlashAttribute("successMessage",
                    "INACTIVE".equalsIgnoreCase(status.trim())
                            ? "Đã ngưng hoạt động shop."
                            : "Đã kích hoạt shop.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }

        redirectAttributes.addAttribute("keyword", keyword == null ? "" : keyword.trim());
        redirectAttributes.addAttribute("status", shopService.normalizeFilterStatus(selectedStatus));
        redirectAttributes.addAttribute("page", Math.max(page, 1));
        return "redirect:/admin/shops";
    }
}
