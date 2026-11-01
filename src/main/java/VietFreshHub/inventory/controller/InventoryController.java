package VietFreshHub.inventory.controller;

import VietFreshHub.inventory.dto.ActivityHistoryFilter;
import VietFreshHub.inventory.dto.BatchRequest;
import VietFreshHub.inventory.dto.BatchResponse;
import VietFreshHub.inventory.dto.InventoryCatalogFilter;
import VietFreshHub.inventory.dto.MarkdownRequest;
import VietFreshHub.inventory.dto.StockChangeRequest;
import VietFreshHub.inventory.rule.InventoryRules;
import VietFreshHub.inventory.service.InventoryService;
import VietFreshHub.product.exception.CatalogException;
import VietFreshHub.product.service.ProductService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
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
import java.net.URI;
import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/seller/inventory")
public class InventoryController {

    private static final String INVENTORY_PATH = "/seller/inventory";

    private final InventoryService inventoryService;
    private final ProductService productService;

    /** Giữ màn nguồn qua các bước xem chi tiết, nhập kho và xử lý lô. */
    @ModelAttribute("inventoryCurrentUrl")
    public String inventoryCurrentUrl(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return request.getQueryString() == null ? path : path + "?" + request.getQueryString();
    }

    /** Chỉ cho quay về các màn GET nội bộ; giữ nguyên bộ lọc và phân trang của màn nguồn. */
    @ModelAttribute("inventoryReturnUrl")
    public String inventoryReturnUrl(
            @RequestParam(required = false) String returnTo, HttpServletRequest request) {
        if (returnTo == null || returnTo.isBlank() || returnTo.length() > 4096) {
            return INVENTORY_PATH;
        }
        String context = request.getContextPath();
        if (!context.isEmpty() && returnTo.startsWith(context + "/")) {
            returnTo = returnTo.substring(context.length());
        }
        try {
            URI target = URI.create(returnTo);
            if (isAllowedReturnTarget(target)) {
                return returnTo;
            }
        } catch (IllegalArgumentException ignored) {
            // Đường dẫn không hợp lệ dùng màn danh sách kho làm điểm quay lại dự phòng.
        }
        return INVENTORY_PATH;
    }

    private boolean isAllowedReturnTarget(URI target) {
        String path = target.getRawPath();
        if (target.isAbsolute() || target.getRawAuthority() != null || path == null) {
            return false;
        }
        return path.matches("/seller/inventory(?:/audit|/batches/[0-9]+/label)?")
                || path.matches("/seller/products/[0-9]+");
    }

    /** Hiển thị danh sách tồn kho theo bộ lọc, kèm danh mục và ngày hiện tại của cửa hàng. */
    @GetMapping
    public String list(
            Authentication authentication,
            @ModelAttribute("inventoryFilters") InventoryCatalogFilter filters,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "5") int size,
            Model model) {
        model.addAttribute(
                "rows",
                productService.searchInventory(authentication, filters, page, size));
        model.addAttribute("categories", productService.categories());
        model.addAttribute("catalogSummary", productService.catalogSummary(authentication));
        model.addAttribute("q", filters.getQ());
        model.addAttribute("category", filters.getCategory());
        model.addAttribute("stock", filters.getStock());
        model.addAttribute("expiry", filters.getExpiry());
        model.addAttribute("today", InventoryRules.today());
        return "inventory/list";
    }

    /** Mở biểu mẫu nhập kho cho phân loại, mặc định ngày nhập là hôm nay tại Việt Nam. */
    @GetMapping("/products/{product}/variants/{variant}/restock")
    public String restockForm(
            Authentication authentication,
            @PathVariable Long product,
            @PathVariable Long variant,
            Model model) {
        BatchRequest form = new BatchRequest();
        form.setReceivedDate(InventoryRules.today());
        model.addAttribute("form", form);
        return showRestockForm(authentication, product, variant, model);
    }

    /** Nhập lô hàng từ biểu mẫu hợp lệ; nếu lỗi thì giữ dữ liệu và hiển thị lại trang nhập kho. */
    @PostMapping("/products/{product}/variants/{variant}/restock")
    public String restock(
            Authentication authentication,
            @PathVariable Long product,
            @PathVariable Long variant,
            @Valid @ModelAttribute("form") BatchRequest form,
            BindingResult errors,
            @ModelAttribute("inventoryReturnUrl") String returnUrl,
            Model model,
            RedirectAttributes flash) {
        // Nạp dữ liệu SKU để kiểm tra quyền và quan hệ sản phẩm trước cả nhánh trả lỗi validation.
        showRestockForm(authentication, product, variant, model);
        if (errors.hasErrors()) {
            return "inventory/restock";
        }
        try {
            inventoryService.restock(authentication, variant, form);
            flash.addFlashAttribute("success", "Đã nhập kho và ghi nhận biến động kho.");
            return "redirect:" + returnUrl;
        } catch (CatalogException exception) {
            errors.reject("business", exception.getMessage());
            return "inventory/restock";
        }
    }

    /** Kiểm tra phân loại thuộc sản phẩm và cửa hàng, rồi nạp dữ liệu cho trang nhập kho. */
    private String showRestockForm(
            Authentication authentication, Long productId, Long variantId, Model model) {
        model.addAttribute(
                "variant", productService.getVariant(authentication, productId, variantId));
        model.addAttribute("productId", productId);
        model.addAttribute("variantId", variantId);
        model.addAttribute("today", InventoryRules.today());
        return "inventory/restock";
    }

    /**
     * Mở đúng biểu mẫu sửa, điều chỉnh, hủy hoặc giảm giá cho lô; từ chối tên thao tác không hỗ trợ.
     */
    @GetMapping("/batches/{id}/{action}")
    public String batchForm(
            Authentication authentication,
            @PathVariable Long id,
            @PathVariable String action,
            Model model) {
        if (!List.of("edit", "adjust", "dispose", "markdown").contains(action)) {
            throw new CatalogException("Thao tác không hợp lệ.");
        }
        BatchResponse batch = inventoryService.batch(authentication, id);
        Object form = createBatchActionForm(authentication, id, action, batch);
        model.addAttribute("form", form);
        addBatchAttributes(batch, id, action, model);
        return "inventory/batch-form";
    }

    private Object createBatchActionForm(
            Authentication authentication, Long batchId, String action, BatchResponse batch) {
        return switch (action) {
            case "edit" -> inventoryService.batchForm(authentication, batchId);
            case "markdown" -> {
                MarkdownRequest form = new MarkdownRequest();
                form.setEndDate(batch.getBatch().getExpiryDate());
                yield form;
            }
            default -> new StockChangeRequest();
        };
    }

    /** Lưu ngày và giá vốn của lô; lỗi được hiển thị trên biểu mẫu, tồn kho không đổi. */
    @PostMapping("/batches/{id}/edit")
    public String edit(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @ModelAttribute("form") BatchRequest form,
            BindingResult errors,
            @ModelAttribute("inventoryReturnUrl") String returnUrl,
            Model model,
            RedirectAttributes flash) {
        if (errors.hasErrors()) {
            return showBatchForm(authentication, id, "edit", model);
        }
        try {
            inventoryService.updateBatch(authentication, id, form);
            flash.addFlashAttribute("success", "Đã cập nhật thông tin lô. Số lượng tồn kho được giữ nguyên.");
            return "redirect:" + returnUrl;
        } catch (CatalogException exception) {
            errors.reject("business", exception.getMessage());
            return showBatchForm(authentication, id, "edit", model);
        }
    }

    /** Gửi yêu cầu điều chỉnh số lượng lô và hiển thị lại biểu mẫu nếu dữ liệu không hợp lệ. */
    @PostMapping("/batches/{id}/adjust")
    public String adjust(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @ModelAttribute("form") StockChangeRequest form,
            BindingResult errors,
            @ModelAttribute("inventoryReturnUrl") String returnUrl,
            Model model,
            RedirectAttributes flash) {
        if (errors.hasErrors()) {
            return showBatchForm(authentication, id, "adjust", model);
        }
        try {
            inventoryService.adjust(authentication, id, form);
            flash.addFlashAttribute("success", "Đã điều chỉnh tồn kho.");
            return "redirect:" + returnUrl;
        } catch (CatalogException exception) {
            errors.reject("business", exception.getMessage());
            return showBatchForm(authentication, id, "adjust", model);
        }
    }

    /** Hủy phần hàng hết hạn có thể hủy của một lô và đưa thông báo kết quả về màn nguồn. */
    @PostMapping("/batches/{id}/dispose")
    public String dispose(
            Authentication authentication,
            @PathVariable Long id,
            @ModelAttribute("inventoryReturnUrl") String returnUrl,
            RedirectAttributes flash) {
        try {
            flash.addFlashAttribute("success", inventoryService.dispose(authentication, id).getMessage());
        } catch (CatalogException exception) {
            flash.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:" + returnUrl;
    }

    /** Hủy phần hàng hết hạn có thể hủy của toàn bộ sản phẩm và thông báo kết quả. */
    @PostMapping("/products/{id}/dispose-expired")
    public String disposeProduct(Authentication authentication, @PathVariable Long id,
            @ModelAttribute("inventoryReturnUrl") String returnUrl, RedirectAttributes flash) {
        try {
            flash.addFlashAttribute("success", inventoryService.disposeExpiredProduct(authentication, id).getMessage());
        } catch (CatalogException exception) {
            flash.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:" + returnUrl;
    }

    /** Thiết lập giảm giá cho lô hợp lệ; hiển thị lỗi dữ liệu hoặc nghiệp vụ ngay trên biểu mẫu. */
    @PostMapping("/batches/{id}/markdown")
    public String markdown(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @ModelAttribute("form") MarkdownRequest form,
            BindingResult errors,
            @ModelAttribute("inventoryReturnUrl") String returnUrl,
            Model model,
            RedirectAttributes flash) {
        if (errors.hasErrors()) {
            return showBatchForm(authentication, id, "markdown", model);
        }
        try {
            inventoryService.markdown(authentication, id, form);
            flash.addFlashAttribute("success", "Đã thiết lập giảm giá cho lô cận hạn.");
            return "redirect:" + returnUrl;
        } catch (CatalogException exception) {
            errors.reject("business", exception.getMessage());
            return showBatchForm(authentication, id, "markdown", model);
        }
    }

    /** Gửi yêu cầu khóa hoặc mở khóa lô với lý do và hiển thị kết quả trên màn nguồn. */
    @PostMapping("/batches/{id}/block")
    public String block(
            Authentication authentication,
            @PathVariable Long id,
            @RequestParam boolean blocked,
            @RequestParam String reason,
            @ModelAttribute("inventoryReturnUrl") String returnUrl,
            RedirectAttributes flash) {
        try {
            inventoryService.block(authentication, id, blocked, reason);
            flash.addFlashAttribute("success", blocked ? "Đã khóa lô." : "Đã mở khóa lô.");
        } catch (CatalogException exception) {
            flash.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:" + returnUrl;
    }

    /** Dừng giảm giá của lô với lý do được nhập, sau đó chuyển về màn nguồn. */
    @PostMapping("/batches/{id}/cancel-markdown")
    public String cancel(
            Authentication authentication,
            @PathVariable Long id,
            @RequestParam String reason,
            @ModelAttribute("inventoryReturnUrl") String returnUrl,
            RedirectAttributes flash) {
        try {
            inventoryService.cancelMarkdown(authentication, id, reason);
            flash.addFlashAttribute("success", "Đã dừng giảm giá cho lô.");
        } catch (CatalogException exception) {
            flash.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:" + returnUrl;
    }

    /** Nạp lại thông tin lô để hiển thị biểu mẫu thao tác sau khi kiểm tra dữ liệu thất bại. */
    private String showBatchForm(
            Authentication authentication, Long batchId, String action, Model model) {
        // Chỉ nạp lại thông tin phụ; form và BindingResult hiện có được giữ để hiển thị dữ liệu lỗi.
        addBatchAttributes(inventoryService.batch(authentication, batchId), batchId, action, model);
        return "inventory/batch-form";
    }

    /** Bổ sung lô, thao tác, ngày hiện tại và lý do hủy mặc định cho biểu mẫu dùng chung. */
    private void addBatchAttributes(BatchResponse batch, Long batchId, String action, Model model) {
        model.addAttribute("row", batch);
        model.addAttribute("batchId", batchId);
        model.addAttribute("action", action);
        model.addAttribute("today", InventoryRules.today());
        model.addAttribute("disposalReason", InventoryRules.EXPIRED_DISPOSAL_REASON);
    }

    /** Hiển thị lịch sử hoạt động có phân trang và các lựa chọn lọc hành động, đối tượng. */
    @GetMapping("/audit")
    public String audit(
            Authentication authentication,
            @ModelAttribute("activityFilters") ActivityHistoryFilter filters,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "5") int size,
            Model model) {
        model.addAttribute("events", inventoryService.activityHistory(authentication, filters, page, size));
        model.addAttribute("activityActions", ActivityHistoryFilter.ACTIONS);
        model.addAttribute("activityEntities", ActivityHistoryFilter.ENTITIES);
        return "inventory/audit";
    }

    /** Hiển thị chi tiết lô sau khi kiểm tra lô thuộc cửa hàng đang quản lý. */
    @GetMapping("/batches/{id}/label")
    public String batchDetails(Authentication authentication, @PathVariable Long id, Model model) {
        model.addAttribute("row", inventoryService.batch(authentication, id));
        return "inventory/batch-detail";
    }
}
