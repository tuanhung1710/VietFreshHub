package VietFreshHub.product.exception;

import VietFreshHub.inventory.controller.InventoryController;
import VietFreshHub.product.controller.ProductController;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@ControllerAdvice(assignableTypes = {ProductController.class, InventoryController.class})
public class ProductInventoryExceptionHandler {

    /** Dữ liệu không chuyển đổi được phải hiển thị trang lỗi MVC với HTTP 400. */
    @ExceptionHandler({BindException.class, MethodArgumentTypeMismatchException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String invalidInput(Model model) {
        model.addAttribute(
                "message",
                "Dữ liệu biểu mẫu hoặc bộ lọc không hợp lệ. Vui lòng kiểm tra lại giá trị đã nhập.");
        return "product/error";
    }

    /** Hiển thị lỗi không có quyền truy cập sản phẩm hoặc kho với mã HTTP 403. */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String denied(AccessDeniedException exception, Model model) {
        model.addAttribute("message", exception.getMessage());
        return "product/error";
    }

    /** Hiển thị lỗi vi phạm quy tắc nghiệp vụ sản phẩm hoặc kho với mã HTTP 400. */
    @ExceptionHandler(CatalogException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String rule(CatalogException exception, Model model) {
        model.addAttribute("message", exception.getMessage());
        return "product/error";
    }

    /** Thông báo xung đột dữ liệu hoặc trùng mã với HTTP 409 để người dùng kiểm tra và thử lại. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String conflict(Model model) {
        model.addAttribute(
                "message", "Dữ liệu đã thay đổi hoặc mã bị trùng. Vui lòng kiểm tra và thử lại.");
        return "product/error";
    }
}
