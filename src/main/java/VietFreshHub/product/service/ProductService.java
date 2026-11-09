package VietFreshHub.product.service;

import VietFreshHub.inventory.dto.InventoryAttentionSummary;
import VietFreshHub.product.dto.ActionRequest;
import VietFreshHub.product.dto.ProductCatalogFilter;
import VietFreshHub.product.dto.ProductRequest;
import VietFreshHub.product.dto.ProductResponse;
import VietFreshHub.product.dto.ProductTabSummary;
import VietFreshHub.product.dto.VariantActionRequest;
import VietFreshHub.product.dto.VariantRequest;
import VietFreshHub.product.dto.VariantResponse;
import VietFreshHub.product.entity.Category;

import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;

import java.util.List;

public interface ProductService {

    /** Lấy danh sách quản lý sản phẩm theo bộ lọc, có phân trang. */
    Page<ProductResponse> searchManagement(
            Authentication authentication, ProductCatalogFilter filters, int page, int size);

    /** Đếm sản phẩm theo các nhóm trạng thái quản lý trên toàn cửa hàng. */
    ProductTabSummary productTabSummary(Authentication authentication);

    /** Tổng hợp cảnh báo tồn thấp, cận hạn và hết hạn trên toàn cửa hàng. */
    InventoryAttentionSummary inventoryAttentionSummary(Authentication authentication);

    /** Lấy chi tiết sản phẩm sau khi kiểm tra quyền quản lý cửa hàng. */
    ProductResponse get(Authentication authentication, Long productId);

    /** Lấy phân loại thuộc đúng sản phẩm và cửa hàng của người quản lý. */
    VariantResponse getVariant(Authentication authentication, Long productId, Long variantId);

    /** Lấy danh mục còn hoạt động ở toàn bộ các cấp cha. */
    List<Category> categories();

    /** Lấy danh sách đơn vị bán được hỗ trợ. */
    List<String> units();

    /** Chuẩn bị dữ liệu biểu mẫu sửa sản phẩm hiện có. */
    ProductRequest editForm(Authentication authentication, Long productId);

    /** Chuẩn bị dữ liệu biểu mẫu sửa phân loại hiện có. */
    VariantRequest variantForm(Authentication authentication, Long productId, Long variantId);

    /** Tạo sản phẩm cùng phân loại, ảnh và tồn ban đầu; trả ID sản phẩm vừa tạo. */
    Long create(Authentication authentication, ProductRequest form);

    /** Cập nhật thông tin sản phẩm và ghi nhận các thay đổi cần hậu kiểm. */
    void update(Authentication authentication, Long productId, ProductRequest form);

    /** Đổi trạng thái kinh doanh của sản phẩm sau khi kiểm tra điều kiện mở bán hoặc ngừng bán. */
    void changeStatus(Authentication authentication, Long productId, ActionRequest form);

    /** Thêm phân loại và tồn ban đầu cho sản phẩm thuộc quyền quản lý. */
    void addVariant(Authentication authentication, Long productId, VariantRequest form);

    /** Cập nhật phân loại, giữ nguyên SKU và ghi nhận thay đổi giá hoặc thông tin. */
    void updateVariant(
            Authentication authentication, Long productId, Long variantId, VariantRequest form);

    /** Xóa mềm phân loại; tự tạm ẩn sản phẩm nếu không còn phân loại đang bật. */
    void deleteVariant(
            Authentication authentication, Long productId, Long variantId, String reason);

    /** Đổi trạng thái phân loại, bảo đảm các điều kiện về hàng giữ và phân loại đang bật. */
    void changeVariantStatus(
            Authentication authentication,
            Long productId,
            Long variantId,
            VariantActionRequest form);

}
