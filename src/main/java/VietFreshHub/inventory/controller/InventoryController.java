package VietFreshHub.inventory.controller;

import VietFreshHub.inventory.dto.BatchRequest;
import VietFreshHub.inventory.dto.ActivityHistoryFilter;
import VietFreshHub.inventory.dto.BatchResponse;
import VietFreshHub.inventory.dto.MarkdownRequest;
import VietFreshHub.inventory.dto.StockChangeRequest;
import VietFreshHub.inventory.rule.InventoryRules;
import VietFreshHub.inventory.service.InventoryService;
import VietFreshHub.product.exception.CatalogException;
import VietFreshHub.product.service.ProductService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;
import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/seller/inventory")
public class InventoryController {

    private static final String INVENTORY_REDIRECT = "redirect:/seller/inventory";

    private final InventoryService inventoryService;
    private final ProductService productService;

    @GetMapping
    public String list(
            Authentication authentication,
            @ModelAttribute("inventoryFilters") VietFreshHub.inventory.dto.InventoryCatalogFilter filters,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int size,
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

    @PostMapping("/products/{product}/variants/{variant}/restock")
    public String restock(
            Authentication authentication,
            @PathVariable Long product,
            @PathVariable Long variant,
            @Valid @ModelAttribute("form") BatchRequest form,
            BindingResult errors,
            Model model,
            RedirectAttributes flash) {
        showRestockForm(authentication, product, variant, model);
        if (errors.hasErrors()) {
            return "inventory/restock";
        }
        try {
            inventoryService.restock(authentication, variant, form);
            flash.addFlashAttribute("success", "Đã nhập kho và ghi nhận biến động kho.");
            return INVENTORY_REDIRECT;
        } catch (CatalogException exception) {
            errors.reject("business", exception.getMessage());
            return "inventory/restock";
        }
    }

    private String showRestockForm(
            Authentication authentication, Long productId, Long variantId, Model model) {
        model.addAttribute(
                "variant", productService.getVariant(authentication, productId, variantId));
        model.addAttribute("productId", productId);
        model.addAttribute("variantId", variantId);
        model.addAttribute("today", InventoryRules.today());
        return "inventory/restock";
    }

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
        Object form;
        if (action.equals("edit")) {
            form = inventoryService.batchForm(authentication, id);
        } else if (action.equals("markdown")) {
            MarkdownRequest markdown = new MarkdownRequest();
            markdown.setEndDate(batch.getBatch().getExpiryDate());
            form = markdown;
        } else {
            form = new StockChangeRequest();
        }
        model.addAttribute("form", form);
        addBatchAttributes(batch, id, action, model);
        return "inventory/batch-form";
    }

    @PostMapping("/batches/{id}/edit")
    public String edit(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @ModelAttribute("form") BatchRequest form,
            BindingResult errors,
            Model model,
            RedirectAttributes flash) {
        if (errors.hasErrors()) {
            return showBatchForm(authentication, id, "edit", model);
        }
        try {
            inventoryService.updateBatch(authentication, id, form);
            flash.addFlashAttribute("success", "Đã cập nhật thông tin lô. Số lượng tồn kho được giữ nguyên.");
            return INVENTORY_REDIRECT;
        } catch (CatalogException exception) {
            errors.reject("business", exception.getMessage());
            return showBatchForm(authentication, id, "edit", model);
        }
    }

    @PostMapping("/batches/{id}/adjust")
    public String adjust(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @ModelAttribute("form") StockChangeRequest form,
            BindingResult errors,
            Model model,
            RedirectAttributes flash) {
        if (errors.hasErrors()) {
            return showBatchForm(authentication, id, "adjust", model);
        }
        try {
            inventoryService.adjust(authentication, id, form);
            flash.addFlashAttribute("success", "Đã điều chỉnh tồn kho.");
            return INVENTORY_REDIRECT;
        } catch (CatalogException exception) {
            errors.reject("business", exception.getMessage());
            return showBatchForm(authentication, id, "adjust", model);
        }
    }

    @PostMapping("/batches/{id}/dispose")
    public String dispose(
            Authentication authentication,
            @PathVariable Long id,
            RedirectAttributes flash) {
        try {
            flash.addFlashAttribute("success", inventoryService.dispose(authentication, id).getMessage());
        } catch (CatalogException exception) {
            flash.addFlashAttribute("error", exception.getMessage());
        }
        return INVENTORY_REDIRECT;
    }

    @PostMapping("/products/{id}/dispose-expired")
    public String disposeProduct(Authentication authentication, @PathVariable Long id, RedirectAttributes flash) {
        try {
            flash.addFlashAttribute("success", inventoryService.disposeExpiredProduct(authentication, id).getMessage());
        } catch (CatalogException exception) {
            flash.addFlashAttribute("error", exception.getMessage());
        }
        return INVENTORY_REDIRECT;
    }

    @PostMapping("/batches/{id}/markdown")
    public String markdown(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @ModelAttribute("form") MarkdownRequest form,
            BindingResult errors,
            Model model,
            RedirectAttributes flash) {
        if (errors.hasErrors()) {
            return showBatchForm(authentication, id, "markdown", model);
        }
        try {
            inventoryService.markdown(authentication, id, form);
            flash.addFlashAttribute("success", "Đã thiết lập giảm giá cho lô cận hạn.");
            return INVENTORY_REDIRECT;
        } catch (CatalogException exception) {
            errors.reject("business", exception.getMessage());
            return showBatchForm(authentication, id, "markdown", model);
        }
    }

    @PostMapping("/batches/{id}/block")
    public String block(
            Authentication authentication,
            @PathVariable Long id,
            @RequestParam boolean blocked,
            @RequestParam String reason,
            RedirectAttributes flash) {
        try {
            inventoryService.block(authentication, id, blocked, reason);
            flash.addFlashAttribute("success", blocked ? "Đã khóa lô." : "Đã mở khóa lô.");
        } catch (CatalogException exception) {
            flash.addFlashAttribute("error", exception.getMessage());
        }
        return INVENTORY_REDIRECT;
    }

    @PostMapping("/batches/{id}/cancel-markdown")
    public String cancel(
            Authentication authentication,
            @PathVariable Long id,
            @RequestParam String reason,
            RedirectAttributes flash) {
        try {
            inventoryService.cancelMarkdown(authentication, id, reason);
            flash.addFlashAttribute("success", "Đã dừng giảm giá cho lô.");
        } catch (CatalogException exception) {
            flash.addFlashAttribute("error", exception.getMessage());
        }
        return INVENTORY_REDIRECT;
    }

    private String showBatchForm(
            Authentication authentication, Long batchId, String action, Model model) {
        addBatchAttributes(inventoryService.batch(authentication, batchId), batchId, action, model);
        return "inventory/batch-form";
    }

    private void addBatchAttributes(BatchResponse batch, Long batchId, String action, Model model) {
        model.addAttribute("row", batch);
        model.addAttribute("batchId", batchId);
        model.addAttribute("action", action);
        model.addAttribute("today", InventoryRules.today());
        model.addAttribute("disposalReason", InventoryRules.EXPIRED_DISPOSAL_REASON);
    }

    @GetMapping("/journal")
    public String journal(
            Authentication authentication,
            @RequestParam(required = false) Long variant,
            @RequestParam(required = false) Long batch,
            @RequestParam(defaultValue = "") String type,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int size,
            Model model) {
        model.addAttribute(
                "events",
                inventoryService.journal(authentication, variant, batch, type, q, from, to, page, size));
        model.addAttribute("variant", variant);
        model.addAttribute("batch", batch);
        model.addAttribute("batchCode", inventoryService.batchCode(batch));
        model.addAttribute("type", type);
        model.addAttribute("q", q);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        return "inventory/journal";
    }

    @GetMapping("/audit")
    public String audit(
            Authentication authentication,
            @ModelAttribute("activityFilters") ActivityHistoryFilter filters,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int size,
            Model model) {
        model.addAttribute("events", inventoryService.activityHistory(authentication, filters, page, size));
        model.addAttribute("activityActions", ActivityHistoryFilter.ACTIONS);
        model.addAttribute("activityEntities", ActivityHistoryFilter.ENTITIES);
        return "inventory/audit";
    }

    @GetMapping("/batches/{id}/label")
    public String batchDetails(Authentication authentication, @PathVariable Long id, Model model) {
        model.addAttribute("row", inventoryService.batch(authentication, id));
        return "inventory/batch-detail";
    }
}
