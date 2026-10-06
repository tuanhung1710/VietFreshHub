package VietFreshHub.Product.exception;
import VietFreshHub.Product.controller.ProductController;
import VietFreshHub.Inventory.controller.InventoryController;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.ui.Model;
import org.springframework.http.HttpStatus;
@ControllerAdvice(assignableTypes={ProductController.class,InventoryController.class})
public class ProductInventoryExceptionHandler {
    @ExceptionHandler(AccessDeniedException.class) @ResponseStatus(HttpStatus.FORBIDDEN)
    public String denied(AccessDeniedException e,Model m){m.addAttribute("message",e.getMessage());return "product/error";}
    @ExceptionHandler(CatalogException.class) @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String rule(CatalogException e,Model m){m.addAttribute("message",e.getMessage());return "product/error";}
    @ExceptionHandler(DataIntegrityViolationException.class) @ResponseStatus(HttpStatus.CONFLICT)
    public String conflict(Model m){m.addAttribute("message","Dữ liệu đã thay đổi hoặc mã SKU/lô bị trùng. Kiểm tra và thử lại.");return "product/error";}
}

