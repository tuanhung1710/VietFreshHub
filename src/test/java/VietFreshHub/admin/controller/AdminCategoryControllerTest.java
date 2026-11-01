package VietFreshHub.admin.controller;

import VietFreshHub.admin.dto.AdminCategoryRequest;
import VietFreshHub.admin.service.AdminCategoryService;
import VietFreshHub.product.enums.CategoryStatus;
import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminCategoryControllerTest {

    private static final String DATABASE_GUARD_MESSAGE =
            "Danh muc/thay doi cha se an san pham dang ban. Chuyen san pham INACTIVE hoac gan danh muc khac truoc.";
    private static final String FRIENDLY_MESSAGE =
            "Chưa thể tắt danh mục hoặc chuyển danh mục này sang nhóm khác vì vẫn còn sản phẩm đang bán bên trong. "
                    + "Hãy ngừng bán các sản phẩm đó hoặc chuyển chúng sang danh mục khác rồi thử lại nhé.";

    @Test
    void statusChangeShowsFriendlyMessageForActiveProductDatabaseGuard() {
        AdminCategoryService service = serviceThrowingOnStatusChange(databaseException(DATABASE_GUARD_MESSAGE));
        AdminCategoryController controller = new AdminCategoryController(service);
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        String view = controller.changeStatus(
                1L, CategoryStatus.INACTIVE, "", "ALL", redirectAttributes);

        assertEquals("redirect:/admin/categories", view);
        assertEquals(FRIENDLY_MESSAGE, redirectAttributes.getFlashAttributes().get("errorMessage"));
    }

    @Test
    void parentChangeShowsFriendlyMessageForActiveProductDatabaseGuard() {
        AdminCategoryService service = serviceThrowingOnUpdate(databaseException(DATABASE_GUARD_MESSAGE));
        AdminCategoryController controller = new AdminCategoryController(service);
        AdminCategoryRequest form = new AdminCategoryRequest();
        form.setName("Trái cây");
        form.setSlug("trai-cay");
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(form, "form");
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.update(
                1L, form, bindingResult, "", "ALL", model, new RedirectAttributesModelMap());

        assertEquals("admin/categories", view);
        assertTrue(bindingResult.getGlobalErrors().stream()
                .anyMatch(error -> FRIENDLY_MESSAGE.equals(error.getDefaultMessage())));
    }

    @Test
    void unrelatedDatabaseErrorIsNotReportedAsActiveProductGuard() {
        AdminCategoryService service = serviceThrowingOnStatusChange(
                databaseException("Login failed for SQL Server user."));
        AdminCategoryController controller = new AdminCategoryController(service);

        assertThrows(InvalidDataAccessResourceUsageException.class,
                () -> controller.changeStatus(
                        1L, CategoryStatus.INACTIVE, "", "ALL", new RedirectAttributesModelMap()));
    }

    private AdminCategoryService serviceThrowingOnStatusChange(RuntimeException exception) {
        return new AdminCategoryService(null) {
            @Override
            public void changeStatus(Long categoryId, CategoryStatus status) {
                throw exception;
            }

            @Override
            public String normalizeStatus(String status) {
                return status == null ? "ALL" : status;
            }
        };
    }

    private AdminCategoryService serviceThrowingOnUpdate(RuntimeException exception) {
        return new AdminCategoryService(null) {
            @Override
            public VietFreshHub.product.entity.Category update(
                    Long categoryId, AdminCategoryRequest request) {
                throw exception;
            }

            @Override
            public java.util.List<CategoryRow> list(String keyword, String status) {
                return java.util.List.of();
            }

            @Override
            public java.util.List<CategoryRow> parentOptions(Long editingCategoryId) {
                return java.util.List.of();
            }

            @Override
            public String normalizeStatus(String status) {
                return status == null ? "ALL" : status;
            }
        };
    }

    private InvalidDataAccessResourceUsageException databaseException(String causeMessage) {
        return new InvalidDataAccessResourceUsageException(
                "could not execute statement", new RuntimeException(causeMessage));
    }
}
