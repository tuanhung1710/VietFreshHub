package VietFreshHub.admin.controller;

import VietFreshHub.admin.dto.AdminCategoryRequest;
import VietFreshHub.admin.service.AdminCategoryService;
import VietFreshHub.product.enums.CategoryStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/categories")
public class AdminCategoryController {

    private static final String ACTIVE_PRODUCT_CATEGORY_GUARD =
            "Danh muc/thay doi cha se an san pham dang ban";
    private static final String ACTIVE_PRODUCT_CATEGORY_MESSAGE =
            "Chưa thể tắt danh mục hoặc chuyển danh mục này sang nhóm khác vì vẫn còn sản phẩm đang bán bên trong. "
                    + "Hãy ngừng bán các sản phẩm đó hoặc chuyển chúng sang danh mục khác rồi thử lại nhé.";

    private final AdminCategoryService categoryService;

    @GetMapping
    public String list(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(required = false) Long editId,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        AdminCategoryRequest form = new AdminCategoryRequest();
        if (editId != null) {
            try {
                form = AdminCategoryRequest.from(categoryService.getCategory(editId));
            } catch (IllegalArgumentException exception) {
                redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
                return "redirect:/admin/categories";
            }
        }
        return renderList(keyword, status, form, model);
    }

    @PostMapping
    public String create(
            @Valid @ModelAttribute("form") AdminCategoryRequest form,
            BindingResult bindingResult,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "ALL") String status,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            return renderList(keyword, status, form, model);
        }
        try {
            categoryService.create(form);
            redirectAttributes.addFlashAttribute("successMessage", "Đã tạo danh mục.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            bindingResult.reject("category", exception.getMessage());
            return renderList(keyword, status, form, model);
        }
        addListFilters(redirectAttributes, keyword, status);
        return "redirect:/admin/categories";
    }

    @PostMapping("/{categoryId}")
    public String update(
            @PathVariable Long categoryId,
            @Valid @ModelAttribute("form") AdminCategoryRequest form,
            BindingResult bindingResult,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "ALL") String status,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        form.setCategoryId(categoryId);
        if (bindingResult.hasErrors()) {
            return renderList(keyword, status, form, model);
        }
        try {
            categoryService.update(categoryId, form);
            redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật danh mục.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            bindingResult.reject("category", exception.getMessage());
            return renderList(keyword, status, form, model);
        } catch (InvalidDataAccessResourceUsageException exception) {
            if (!isActiveProductCategoryGuard(exception)) {
                throw exception;
            }
            bindingResult.reject("category", ACTIVE_PRODUCT_CATEGORY_MESSAGE);
            return renderList(keyword, status, form, model);
        }
        addListFilters(redirectAttributes, keyword, status);
        return "redirect:/admin/categories";
    }

    @PostMapping("/{categoryId}/status")
    public String changeStatus(
            @PathVariable Long categoryId,
            @RequestParam CategoryStatus status,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "ALL") String selectedStatus,
            RedirectAttributes redirectAttributes
    ) {
        try {
            categoryService.changeStatus(categoryId, status);
            String message = status == CategoryStatus.ACTIVE
                    ? "Đã kích hoạt danh mục."
                    : "Đã ngừng hoạt động danh mục. Danh mục con sẽ không hiển thị trong catalog khi danh mục cha đang inactive.";
            redirectAttributes.addFlashAttribute("successMessage", message);
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        } catch (InvalidDataAccessResourceUsageException exception) {
            if (!isActiveProductCategoryGuard(exception)) {
                throw exception;
            }
            redirectAttributes.addFlashAttribute("errorMessage", ACTIVE_PRODUCT_CATEGORY_MESSAGE);
        }
        addListFilters(redirectAttributes, keyword, selectedStatus);
        return "redirect:/admin/categories";
    }

    private String renderList(String keyword, String status, AdminCategoryRequest form, Model model) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        String selectedStatus = categoryService.normalizeStatus(status);
        String currentImageUrl = null;
        if (form.getCategoryId() != null) {
            try {
                currentImageUrl = categoryService.getCategory(form.getCategoryId()).getImageUrl();
            } catch (IllegalArgumentException ignored) {
                // Keep the form renderable if a category was removed while the edit form was open.
            }
        }
        model.addAttribute("categoryRows", categoryService.list(normalizedKeyword, selectedStatus));
        model.addAttribute("parentOptions", categoryService.parentOptions(form.getCategoryId()));
        model.addAttribute("keyword", normalizedKeyword);
        model.addAttribute("selectedStatus", selectedStatus);
        model.addAttribute("form", form);
        model.addAttribute("currentImageUrl", currentImageUrl);
        return "admin/categories";
    }

    private void addListFilters(RedirectAttributes redirectAttributes, String keyword, String status) {
        redirectAttributes.addAttribute("keyword", keyword == null ? "" : keyword.trim());
        redirectAttributes.addAttribute("status", categoryService.normalizeStatus(status));
    }

    private boolean isActiveProductCategoryGuard(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null && message.toLowerCase(java.util.Locale.ROOT)
                    .contains(ACTIVE_PRODUCT_CATEGORY_GUARD.toLowerCase(java.util.Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
