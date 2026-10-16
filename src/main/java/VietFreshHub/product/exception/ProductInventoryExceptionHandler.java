package VietFreshHub.product.exception;

import VietFreshHub.inventory.controller.InventoryController;
import VietFreshHub.product.controller.ProductController;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

@ControllerAdvice(assignableTypes = {ProductController.class, InventoryController.class})
public class ProductInventoryExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String denied(AccessDeniedException exception, Model model) {
        model.addAttribute("message", exception.getMessage());
        return "product/error";
    }

    @ExceptionHandler(CatalogException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String rule(CatalogException exception, Model model) {
        model.addAttribute("message", exception.getMessage());
        return "product/error";
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String conflict(Model model) {
        model.addAttribute(
                "message", "Dữ liệu đã thay đổi hoặc mã bị trùng. Vui lòng kiểm tra và thử lại.");
        return "product/error";
    }
}
