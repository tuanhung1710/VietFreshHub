package VietFreshHub.product.controller;

import VietFreshHub.inventory.rule.InventoryRules;
import VietFreshHub.product.dto.ActionRequest;
import VietFreshHub.product.dto.BulkProductActionRequest;
import VietFreshHub.product.dto.ProductCatalogFilter;
import VietFreshHub.product.dto.ProductRequest;
import VietFreshHub.product.dto.VariantActionRequest;
import VietFreshHub.product.dto.VariantDeleteRequest;
import VietFreshHub.product.dto.VariantRequest;
import VietFreshHub.product.enums.ProductStatus;
import VietFreshHub.product.enums.VariantStatus;
import VietFreshHub.product.exception.CatalogException;
import VietFreshHub.product.exception.DuplicateProductNameException;
import VietFreshHub.product.service.ProductDraftService;
import VietFreshHub.product.service.ProductService;
import VietFreshHub.shop.service.ShopService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindException;
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

    private static final String VARIANT_DELETED_MESSAGE =
            "Đã xóa phân loại khỏi danh sách. Lịch sử và tồn kho vẫn được lưu. "
                    + "Nếu không còn phân loại đang bật, sản phẩm đã tự tạm ẩn.";

    private final ProductService productService;
    private final ShopService shopService;
    private final ProductDraftService productDraftService;

    /**
     * Xử lý thêm, xóa phân loại hoặc dùng ảnh chung trên bản nháp chưa lưu.
     * Nếu chuyển đổi dữ liệu lỗi, giữ nguyên các dòng để thông báo lỗi vẫn trỏ đúng trường.
     */
    @PostMapping("/draft")
    public String draft(
            Authentication authentication,
            @ModelAttribute("form") ProductRequest form,
            BindingResult errors,
            @RequestParam(required = false) String addVariant,
            @RequestParam(required = false) Integer removeVariant,
            @RequestParam(required = false) Integer useProductImage,
            Model model) {
        shopService.getManagedShopId(authentication);
        // Bản nháp có thể chưa đủ dữ liệu; giữ nguyên chỉ số dòng nếu có lỗi chuyển đổi giá trị.
        if (!errors.hasErrors()) {
            try {
                productDraftService.update(
                        form, addVariant != null, removeVariant, useProductImage);
            } catch (CatalogException exception) {
                errors.reject("business", exception.getMessage());
            }
        }
        return showProductForm(model, null);
    }

    /**
     * Hiển thị danh sách sản phẩm, bộ lọc, thống kê và phạm vi lưu lựa chọn theo cửa hàng, tài khoản.
     */
    @GetMapping
    public String list(
            Authentication authentication,
            @Valid @ModelAttribute("filters") ProductCatalogFilter filters,
            BindingResult errors,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model)
            throws BindException {
        if (errors.hasErrors()) {
            throw new BindException(errors);
        }
        model.addAttribute(
                "rows", productService.searchManagement(authentication, filters, page, size));
        model.addAttribute("categories", productService.categories());
        model.addAttribute("tabSummary", productService.productTabSummary(authentication));
        model.addAttribute("q", filters.getQ());
        model.addAttribute("category", filters.getCategory());
        model.addAttribute("tab", filters.getTab());
        model.addAttribute("createdFrom", filters.getCreatedFrom());
        model.addAttribute("createdTo", filters.getCreatedTo());
        model.addAttribute(
                "selectionScope",
                shopService.getManagedShopId(authentication) + ":" + authentication.getName());
        return "product/list";
    }

    /** Mở biểu mẫu tạo sản phẩm với một dòng phân loại ban đầu. */
    @GetMapping("/new")
    public String createForm(Model model) {
        ProductRequest form = new ProductRequest();
        form.getVariants().add(new VariantRequest());
        model.addAttribute("form", form);
        return showProductForm(model, null);
    }

    /** Tạo sản phẩm từ biểu mẫu hợp lệ; nếu có lỗi thì hiển thị lại biểu mẫu kèm thông báo. */
    @PostMapping
    public String create(
            Authentication authentication,
            @Valid @ModelAttribute("form") ProductRequest form,
            BindingResult errors,
            Model model,
            RedirectAttributes flash) {
        // @Valid và bước chuyển đổi kiểu đã chạy; có lỗi thì giữ dữ liệu để người dùng sửa tại chỗ.
        if (errors.hasErrors()) {
            return showProductForm(model, null);
        }
        try {
            Long productId = productService.create(authentication, form);
            flash.addFlashAttribute(
                    "success",
                    "Đã tạo sản phẩm và các phân loại. Sản phẩm được mở bán khi đủ điều kiện.");
            return productRedirect(productId);
        } catch (DuplicateProductNameException exception) {
            errors.rejectValue("name", "duplicate", exception.getMessage());
            return showProductForm(model, null);
        } catch (DataIntegrityViolationException exception) {
            // Hai yêu cầu có thể cùng vượt qua kiểm tra tên; ràng buộc duy nhất ở DB vẫn chặn
            // trùng.
            rejectDuplicateProductName(exception, errors);
            return showProductForm(model, null);
        } catch (CatalogException exception) {
            errors.reject("business", exception.getMessage());
            return showProductForm(model, null);
        }
    }

    /** Hiển thị chi tiết sản phẩm sau khi service kiểm tra quyền quản lý của người dùng. */
    @GetMapping("/{id}")
    public String detail(Authentication authentication, @PathVariable Long id, Model model) {
        model.addAttribute("row", productService.get(authentication, id));
        return "product/detail";
    }

    /** Nạp dữ liệu hiện tại vào biểu mẫu chỉnh sửa sản phẩm. */
    @GetMapping("/{id}/edit")
    public String edit(Authentication authentication, @PathVariable Long id, Model model) {
        model.addAttribute("form", productService.editForm(authentication, id));
        return showProductForm(model, id);
    }

    /** Lưu thông tin sản phẩm hợp lệ và chuyển về chi tiết; lỗi được trả lại ngay trên biểu mẫu. */
    @PostMapping("/{id}/edit")
    public String update(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @ModelAttribute("form") ProductRequest form,
            BindingResult errors,
            Model model,
            RedirectAttributes flash) {
        // Trả lại chính form đang nhập khi lỗi, đồng thời nạp lại danh mục và các lựa chọn phụ.
        if (errors.hasErrors()) {
            return showProductForm(model, id);
        }
        try {
            productService.update(authentication, id, form);
            flash.addFlashAttribute("success", "Đã lưu thông tin sản phẩm.");
            return productRedirect(id);
        } catch (DuplicateProductNameException exception) {
            errors.rejectValue("name", "duplicate", exception.getMessage());
            return showProductForm(model, id);
        } catch (DataIntegrityViolationException exception) {
            rejectDuplicateProductName(exception, errors);
            return showProductForm(model, id);
        } catch (CatalogException exception) {
            errors.reject("business", exception.getMessage());
            return showProductForm(model, id);
        }
    }

    /** Đổi trạng thái sản phẩm và trả về danh sách hoặc chi tiết theo nơi bắt đầu thao tác. */
    @PostMapping("/{id}/status")
    public String status(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @ModelAttribute ActionRequest form,
            BindingResult errors,
            @RequestParam(defaultValue = "false") boolean returnToList,
            @ModelAttribute("filters") ProductCatalogFilter filters,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            RedirectAttributes flash) {
        if (errors.hasErrors()) {
            flash.addFlashAttribute(
                    "error", "Chọn trạng thái và nhập lý do thay đổi, tối đa 900 ký tự.");
            return returnToList ? listRedirect(filters, page, size, flash) : productRedirect(id);
        }
        try {
            productService.changeStatus(authentication, id, form);
            if (form.getStatus() == ProductStatus.DELETED) {
                flash.addFlashAttribute(
                        "success",
                        "Đã xóa sản phẩm. Tồn kho và lịch sử vẫn được lưu trong Kho hàng.");
                return listRedirect(filters, page, size, flash);
            }
            flash.addFlashAttribute("success", "Đã cập nhật trạng thái sản phẩm.");
        } catch (CatalogException exception) {
            flash.addFlashAttribute("error", exception.getMessage());
        }
        return returnToList ? listRedirect(filters, page, size, flash) : productRedirect(id);
    }

    /**
     * Đổi trạng thái danh sách sản phẩm đã chọn và trả lại bộ lọc hiện tại.
     * Khi thành công, gửi các ID đã xử lý để giao diện xóa đúng những lựa chọn đó.
     */
    @PostMapping("/bulk-status")
    public String bulkStatus(
            Authentication authentication,
            @Valid @ModelAttribute BulkProductActionRequest form,
            BindingResult errors,
            @ModelAttribute("filters") ProductCatalogFilter filters,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            RedirectAttributes flash) {
        if (errors.hasErrors()) {
            flash.addFlashAttribute(
                    "error",
                    "Chọn từ 1 đến 50 sản phẩm, trạng thái hợp lệ và nhập lý do tối đa 900 ký tự.");
            return listRedirect(filters, page, size, flash);
        }
        try {
            productService.changeStatuses(authentication, form);
            flash.addFlashAttribute(
                    "success",
                    form.getStatus() == ProductStatus.DELETED
                            ? "Đã xóa "
                                    + form.getProductIds().size()
                                    + " sản phẩm. Tồn kho và lịch sử vẫn được lưu trong Kho hàng."
                            : "Đã cập nhật trạng thái "
                                    + form.getProductIds().size()
                                    + " sản phẩm.");
            // Trang trả về chỉ xóa các ID này khỏi lựa chọn đã lưu khi máy chủ xử lý thành công.
            flash.addFlashAttribute("completedBulkProductIds", form.getProductIds());
        } catch (CatalogException exception) {
            flash.addFlashAttribute("error", exception.getMessage());
        }

        return listRedirect(filters, page, size, flash);
    }

    /** Chuyển về danh sách sản phẩm, giữ nguyên bộ lọc, số trang và kích thước trang. */
    private String listRedirect(
            ProductCatalogFilter filters, int page, int size, RedirectAttributes flash) {
        // Các attribute này trở thành tham số URL để quay về đúng bộ lọc và trang trước thao tác.
        flash.addAttribute("q", filters.getQ());
        flash.addAttribute("category", filters.getCategory());
        flash.addAttribute("tab", filters.getTab());
        flash.addAttribute("sort", filters.getSort());
        flash.addAttribute("createdFrom", filters.getCreatedFrom());
        flash.addAttribute("createdTo", filters.getCreatedTo());
        flash.addAttribute("minPrice", filters.getMinPrice());
        flash.addAttribute("maxPrice", filters.getMaxPrice());
        flash.addAttribute("page", page);
        flash.addAttribute("size", size);
        return "redirect:/seller/products";
    }

    /** Mở biểu mẫu thêm phân loại cho sản phẩm thuộc quyền quản lý. */
    @GetMapping("/{id}/variants/new")
    public String add(Authentication authentication, @PathVariable Long id, Model model) {
        model.addAttribute("form", new VariantRequest());
        return showVariantForm(authentication, model, id, null);
    }

    /** Mở biểu mẫu sửa phân loại với dữ liệu hiện tại. */
    @GetMapping("/{id}/variants/{variant}/edit")
    public String editVariant(
            Authentication authentication,
            @PathVariable Long id,
            @PathVariable Long variant,
            Model model) {
        model.addAttribute("form", productService.variantForm(authentication, id, variant));
        return showVariantForm(authentication, model, id, variant);
    }

    /** Thêm phân loại hợp lệ; khi có lỗi thì giữ dữ liệu và hiển thị lại biểu mẫu. */
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
                    "success", "Đã thêm phân loại. Trạng thái bán hàng được giữ nguyên.");
            return productRedirect(id);
        } catch (CatalogException exception) {
            errors.reject("business", exception.getMessage());
            return showVariantForm(authentication, model, id, null);
        }
    }

    /** Lưu thay đổi phân loại; nếu có lỗi dữ liệu hoặc nghiệp vụ thì hiển thị lại biểu mẫu. */
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
                    "success", "Đã lưu phân loại. Trạng thái bán hàng được giữ nguyên.");
            return productRedirect(id);
        } catch (CatalogException exception) {
            errors.reject("business", exception.getMessage());
            return showVariantForm(authentication, model, id, variant);
        }
    }

    /** Xóa phân loại khỏi danh sách quản lý, giữ lại lịch sử và tồn kho để đối soát. */
    @PostMapping("/{id}/variants/{variant}/delete")
    public String deleteVariant(
            Authentication authentication,
            @PathVariable Long id,
            @PathVariable Long variant,
            @Valid @ModelAttribute VariantDeleteRequest form,
            BindingResult errors,
            @RequestParam(defaultValue = "false") boolean returnToList,
            @ModelAttribute("filters") ProductCatalogFilter filters,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            RedirectAttributes flash) {
        if (errors.hasErrors()) {
            flash.addFlashAttribute("error", "Nhập lý do xóa phân loại, tối đa 900 ký tự.");
            return returnToList ? listRedirect(filters, page, size, flash) : productRedirect(id);
        }
        try {
            productService.deleteVariant(authentication, id, variant, form.getReason());
            flash.addFlashAttribute("success", VARIANT_DELETED_MESSAGE);
        } catch (CatalogException exception) {
            flash.addFlashAttribute("error", exception.getMessage());
        }
        return returnToList ? listRedirect(filters, page, size, flash) : productRedirect(id);
    }

    /** Đổi trạng thái phân loại và quay lại đúng trang danh sách hoặc chi tiết bắt đầu thao tác. */
    @PostMapping("/{id}/variants/{variant}/status")
    public String variantStatus(
            Authentication authentication,
            @PathVariable Long id,
            @PathVariable Long variant,
            @Valid @ModelAttribute VariantActionRequest form,
            BindingResult errors,
            @RequestParam(defaultValue = "false") boolean returnToList,
            @ModelAttribute("filters") ProductCatalogFilter filters,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            RedirectAttributes flash) {
        if (errors.hasErrors()) {
            flash.addFlashAttribute(
                    "error", "Chọn trạng thái và nhập lý do thay đổi, tối đa 900 ký tự.");
            return returnToList ? listRedirect(filters, page, size, flash) : productRedirect(id);
        }
        try {
            productService.changeVariantStatus(authentication, id, variant, form);
            flash.addFlashAttribute(
                    "success",
                    form.getStatus() == VariantStatus.DELETED
                            ? VARIANT_DELETED_MESSAGE
                            : "Đã cập nhật trạng thái phân loại.");
        } catch (CatalogException exception) {
            flash.addFlashAttribute("error", exception.getMessage());
        }
        return returnToList ? listRedirect(filters, page, size, flash) : productRedirect(id);
    }

    /** Xung đột tên do hai request đồng thời cũng được trả về đúng trường trên biểu mẫu. */
    private void rejectDuplicateProductName(
            DataIntegrityViolationException exception, BindingResult errors) {
        // Lỗi SQL thường được bọc qua nhiều lớp exception; tìm đúng tên ràng buộc trong chuỗi
        // nguyên nhân.
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null
                    && cause.getMessage().contains("UX_products_shop_name")) {
                errors.rejectValue("name", "duplicate", DuplicateProductNameException.MESSAGE);
                return;
            }
        }
        // Vi phạm ràng buộc khác phải được xử lý theo lỗi dữ liệu chung, không báo nhầm là trùng
        // tên.
        throw exception;
    }

    /** Bổ sung danh mục, đơn vị bán và ngày hiện tại cho biểu mẫu tạo hoặc sửa sản phẩm. */
    private String showProductForm(Model model, Long productId) {
        model.addAttribute("productId", productId);
        model.addAttribute("categories", productService.categories());
        model.addAttribute("units", productService.units());
        model.addAttribute("today", InventoryRules.today());

        // render trong templates/product/form.html
        return "product/form";
    }

    /** Bổ sung sản phẩm, phân loại, đơn vị và ngày hiện tại cho biểu mẫu phân loại. */
    private String showVariantForm(
            Authentication authentication, Model model, Long productId, Long variantId) {
        model.addAttribute("row", productService.get(authentication, productId));
        model.addAttribute("productId", productId);
        model.addAttribute("variantId", variantId);
        model.addAttribute("units", productService.units());
        model.addAttribute("today", InventoryRules.today());
        return "product/variant-form";
    }

    /** Tạo đường dẫn chuyển về trang chi tiết sản phẩm sau thao tác. */
    private String productRedirect(Long productId) {
        return "redirect:/seller/products/" + productId;
    }
}
