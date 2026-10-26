package VietFreshHub.product.controller;

import VietFreshHub.inventory.rule.InventoryRules;
import VietFreshHub.product.dto.ActionRequest;
import VietFreshHub.product.dto.BulkProductActionRequest;
import VietFreshHub.product.dto.ProductCatalogFilter;
import VietFreshHub.product.dto.ProductRequest;
import VietFreshHub.product.dto.VariantActionRequest;
import VietFreshHub.product.dto.VariantRequest;
import VietFreshHub.product.exception.CatalogException;
import VietFreshHub.product.service.ProductService;
import VietFreshHub.product.service.ProductDraftService;
import VietFreshHub.shop.service.ShopService;

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

@Controller
@RequiredArgsConstructor
@RequestMapping("/seller/products")
public class ProductController {

    private final ProductService productService;
    private final ShopService shopService;
    private final ProductDraftService productDraftService;

    @PostMapping("/draft")
    public String draft(Authentication authentication, @ModelAttribute("form") ProductRequest form,
            BindingResult errors, @RequestParam(required = false) String addVariant,
            @RequestParam(required = false) Integer removeVariant,
            @RequestParam(required = false) Integer useProductImage, Model model) {
        shopService.getManagedShopId(authentication);
        // Drafts may be incomplete. Show conversion errors before changing row indexes.
        if (!errors.hasErrors()) {
            try {
                InventoryRules.require((addVariant != null ? 1 : 0) + (removeVariant != null ? 1 : 0)
                        + (useProductImage != null ? 1 : 0) == 1, "Chọn một thao tác với phân loại.");
                if (addVariant != null) productDraftService.addVariant(form);
                else if (removeVariant != null) productDraftService.removeVariant(form, removeVariant);
                else productDraftService.useProductImage(form, useProductImage);
            } catch (CatalogException exception) {
                errors.reject("business", exception.getMessage());
            }
        }
        return showProductForm(model, null);
    }

    @GetMapping
    public String list(
            Authentication authentication,
            @ModelAttribute("filters") ProductCatalogFilter filters,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int size,
            Model model) {
        model.addAttribute("rows", productService.searchManagement(authentication, filters, page, size));
        model.addAttribute("categories", productService.categories());
        model.addAttribute("catalogSummary", productService.catalogSummary(authentication));
        model.addAttribute("tabSummary", productService.productTabSummary(authentication));
        model.addAttribute("q", filters.getQ());
        model.addAttribute("category", filters.getCategory());
        model.addAttribute("tab", filters.getTab());
        model.addAttribute("createdFrom", filters.getCreatedFrom());
        model.addAttribute("createdTo", filters.getCreatedTo());
        model.addAttribute("selectionScope", shopService.getManagedShopId(authentication) + ":" + authentication.getName());
        return "product/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        ProductRequest form = new ProductRequest();
        form.getVariants().add(new VariantRequest());
        model.addAttribute("form", form);
        return showProductForm(model, null);
    }

    @PostMapping
    public String create(
            Authentication authentication,
            @Valid @ModelAttribute("form") ProductRequest form,
            BindingResult errors,
            Model model,
            RedirectAttributes flash) {
        if (errors.hasErrors()) {
            return showProductForm(model, null);
        }
        try {
            Long productId = productService.create(authentication, form);
            flash.addFlashAttribute(
                    "success", "Đã tạo sản phẩm và các phân loại. Sản phẩm được mở bán khi đủ điều kiện và sẽ được hậu kiểm.");
            return productRedirect(productId);
        } catch (CatalogException exception) {
            errors.reject("business", exception.getMessage());
            return showProductForm(model, null);
        }
    }

    @GetMapping("/{id}")
    public String detail(Authentication authentication, @PathVariable Long id, Model model) {
        model.addAttribute("row", productService.get(authentication, id));
        return "product/detail";
    }

    @GetMapping("/{id}/edit")
    public String edit(Authentication authentication, @PathVariable Long id, Model model) {
        model.addAttribute("form", productService.editForm(authentication, id));
        return showProductForm(model, id);
    }

    @PostMapping("/{id}/edit")
    public String update(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @ModelAttribute("form") ProductRequest form,
            BindingResult errors,
            Model model,
            RedirectAttributes flash) {
        if (errors.hasErrors()) {
            return showProductForm(model, id);
        }
        try {
            productService.update(authentication, id, form);
            flash.addFlashAttribute("success", "Đã lưu thông tin sản phẩm.");
            return productRedirect(id);
        } catch (CatalogException exception) {
            errors.reject("business", exception.getMessage());
            return showProductForm(model, id);
        }
    }

    @PostMapping("/{id}/status")
    public String status(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @ModelAttribute ActionRequest form,
            BindingResult errors,
            @RequestParam(defaultValue = "false") boolean returnToList,
            @ModelAttribute("filters") ProductCatalogFilter filters,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int size,
            RedirectAttributes flash) {
        if (errors.hasErrors()) {
            flash.addFlashAttribute("error", "Chọn trạng thái và nhập lý do thay đổi, tối đa 900 ký tự.");
            return returnToList ? listRedirect(filters, page, size, flash) : productRedirect(id);
        }
        try {
            productService.changeStatus(authentication, id, form);
            flash.addFlashAttribute("success", "Đã cập nhật trạng thái sản phẩm.");
        } catch (CatalogException exception) {
            flash.addFlashAttribute("error", exception.getMessage());
        }
        return returnToList ? listRedirect(filters, page, size, flash) : productRedirect(id);
    }

    @PostMapping("/bulk-status")
    public String bulkStatus(Authentication authentication,
            @Valid @ModelAttribute BulkProductActionRequest form, BindingResult errors,
            @ModelAttribute("filters") ProductCatalogFilter filters,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int size, RedirectAttributes flash) {
        if (errors.hasErrors()) {
            flash.addFlashAttribute("error", "Chọn từ 1 đến 50 sản phẩm, trạng thái hợp lệ và nhập lý do tối đa 900 ký tự.");
        } else {
            try {
                productService.changeStatuses(authentication, form);
                flash.addFlashAttribute("success", "Đã cập nhật trạng thái " + form.getProductIds().size() + " sản phẩm.");
                flash.addFlashAttribute("completedBulkProductIds", form.getProductIds());
            } catch (CatalogException exception) {
                flash.addFlashAttribute("error", exception.getMessage());
            }
        }
        return listRedirect(filters, page, size, flash);
    }

    private String listRedirect(ProductCatalogFilter filters, int page, int size, RedirectAttributes flash) {
        flash.addAttribute("q", filters.getQ());
        flash.addAttribute("category", filters.getCategory());
        flash.addAttribute("tab", filters.getTab());
        flash.addAttribute("stock", filters.getStock());
        flash.addAttribute("sort", filters.getSort());
        flash.addAttribute("approval", filters.getApproval());
        flash.addAttribute("eligibility", filters.getEligibility());
        flash.addAttribute("createdFrom", filters.getCreatedFrom());
        flash.addAttribute("createdTo", filters.getCreatedTo());
        flash.addAttribute("minPrice", filters.getMinPrice());
        flash.addAttribute("maxPrice", filters.getMaxPrice());
        flash.addAttribute("page", page);
        flash.addAttribute("size", size);
        return "redirect:/seller/products";
    }

    @GetMapping("/{id}/variants/new")
    public String add(Authentication authentication, @PathVariable Long id, Model model) {
        model.addAttribute("form", new VariantRequest());
        return showVariantForm(authentication, model, id, null);
    }

    @GetMapping("/{id}/variants/{variant}/edit")
    public String editVariant(
            Authentication authentication,
            @PathVariable Long id,
            @PathVariable Long variant,
            Model model) {
        model.addAttribute("form", productService.variantForm(authentication, id, variant));
        return showVariantForm(authentication, model, id, variant);
    }

    @PostMapping("/{id}/variants")
    public String add(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @ModelAttribute("form") VariantRequest form,
            BindingResult errors,
            Model model,
            RedirectAttributes flash) {
        if (errors.hasErrors()) {
            return showVariantForm(authentication, model, id, null);
        }
        try {
            productService.addVariant(authentication, id, form);
            flash.addFlashAttribute(
                    "success", "Đã thêm phân loại. Thông tin mới sẽ được hậu kiểm; trạng thái bán hàng được giữ nguyên.");
            return productRedirect(id);
        } catch (CatalogException exception) {
            errors.reject("business", exception.getMessage());
            return showVariantForm(authentication, model, id, null);
        }
    }

    @PostMapping("/{id}/variants/{variant}/edit")
    public String editVariant(
            Authentication authentication,
            @PathVariable Long id,
            @PathVariable Long variant,
            @Valid @ModelAttribute("form") VariantRequest form,
            BindingResult errors,
            Model model,
            RedirectAttributes flash) {
        if (errors.hasErrors()) {
            return showVariantForm(authentication, model, id, variant);
        }
        try {
            productService.updateVariant(authentication, id, variant, form);
            flash.addFlashAttribute(
                    "success",
                    "Đã lưu phân loại. Thay đổi thông tin sẽ được hậu kiểm; trạng thái bán hàng được giữ nguyên.");
            return productRedirect(id);
        } catch (CatalogException exception) {
            errors.reject("business", exception.getMessage());
            return showVariantForm(authentication, model, id, variant);
        }
    }

    @PostMapping("/{id}/variants/{variant}/status")
    public String variantStatus(
            Authentication authentication,
            @PathVariable Long id,
            @PathVariable Long variant,
            @Valid @ModelAttribute VariantActionRequest form,
            BindingResult errors,
            RedirectAttributes flash) {
        if (errors.hasErrors()) {
            flash.addFlashAttribute("error", "Chọn trạng thái và nhập lý do thay đổi.");
            return productRedirect(id);
        }
        try {
            productService.changeVariantStatus(authentication, id, variant, form);
            flash.addFlashAttribute("success", "Đã cập nhật trạng thái phân loại.");
        } catch (CatalogException exception) {
            flash.addFlashAttribute("error", exception.getMessage());
        }
        return productRedirect(id);
    }

    @GetMapping("/{id}/variants/{variant}/prices")
    public String prices(
            Authentication authentication,
            @PathVariable Long id,
            @PathVariable Long variant,
            @RequestParam(defaultValue = "1") int page,
            Model model) {
        model.addAttribute(
                "events", productService.priceHistory(authentication, id, variant, page));
        model.addAttribute("productId", id);
        model.addAttribute("variantId", variant);
        return "product/price-history";
    }

    private String showProductForm(Model model, Long productId) {
        model.addAttribute("productId", productId);
        model.addAttribute("categories", productService.categories());
        model.addAttribute("units", productService.units());
        model.addAttribute("today", InventoryRules.today());
        return "product/form";
    }

    private String showVariantForm(
            Authentication authentication, Model model, Long productId, Long variantId) {
        model.addAttribute("row", productService.get(authentication, productId));
        model.addAttribute("productId", productId);
        model.addAttribute("variantId", variantId);
        model.addAttribute("units", productService.units());
        model.addAttribute("today", InventoryRules.today());
        return "product/variant-form";
    }

    private String productRedirect(Long productId) {
        return "redirect:/seller/products/" + productId;
    }
}
