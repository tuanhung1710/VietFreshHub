package VietFreshHub.catalog.controller;

import VietFreshHub.auth.service.CurrentCustomerService;
import VietFreshHub.cart.service.CartService;
import VietFreshHub.catalog.service.StorefrontService;
import VietFreshHub.catalog.entity.CatalogCategory;
import VietFreshHub.exception.ResourceNotFoundException;
import VietFreshHub.shop.controller.ShopBrowseController;
import VietFreshHub.shop.controller.ShopFavoriteController;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.validation.BindException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.util.List;

/** Dữ liệu dùng chung cho header/footer của các trang mua sắm được render bằng Thymeleaf. */
@ControllerAdvice(assignableTypes = {StorefrontHomeController.class, StorefrontCatalogController.class,
        StorefrontPageController.class, ShopBrowseController.class, ShopFavoriteController.class})
@RequiredArgsConstructor
public class StorefrontViewAdvice {
    private final StorefrontService storefront;
    private final CartService carts;
    private final CurrentCustomerService customers;

    @ModelAttribute
    public void addCategories(Model model) {
        List<CatalogCategory> categories = storefront.categories();
        model.addAttribute("categories", categories);
        model.addAttribute("headerCategories", categories.stream().limit(5).toList());
    }

    @ModelAttribute("storefrontCartCount")
    public long cartCount(Authentication authentication) {
        boolean customer = authentication != null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream().anyMatch(a -> "ROLE_CUSTOMER".equals(a.getAuthority()));
        if (!customer) {
            return 0;
        }
        try {
            return carts.getActiveCart(customers.requireCustomerId(authentication)).getTotalItems();
        } catch (AccessDeniedException exception) {
            return 0;
        }
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String unavailable(ResourceNotFoundException exception, Authentication authentication, Model model) {
        // Model của màn lỗi được tạo lại nên cần nạp lại dữ liệu header/footer.
        addCategories(model);
        model.addAttribute("storefrontCartCount", cartCount(authentication));
        model.addAttribute("message", exception.getMessage());
        return "customer/catalog-error";
    }

    @ExceptionHandler({BindException.class, IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String invalidFilters(Exception exception, Authentication authentication, Model model) {
        addCategories(model);
        model.addAttribute("storefrontCartCount", cartCount(authentication));
        model.addAttribute("errorTitle", "Bộ lọc không hợp lệ");
        model.addAttribute("message", exception instanceof BindException errors
                ? errors.getAllErrors().getFirst().getDefaultMessage() : "Vui lòng kiểm tra lại điều kiện tìm kiếm.");
        return "customer/catalog-error";
    }
}
