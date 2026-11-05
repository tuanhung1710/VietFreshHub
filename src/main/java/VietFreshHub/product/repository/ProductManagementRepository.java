package VietFreshHub.product.repository;

import VietFreshHub.inventory.dto.InventoryAttentionSummary;
import VietFreshHub.product.dto.ProductCatalogFilter;
import VietFreshHub.product.dto.ProductManagementState;
import VietFreshHub.product.dto.ProductTabSummary;
import VietFreshHub.product.entity.Product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface ProductManagementRepository {
    /** Lấy trang sản phẩm theo bộ lọc quản lý đầy đủ và danh mục hợp lệ. */
    Page<Product> searchManagement(
            Long shop,
            ProductCatalogFilter filters,
            LocalDateTime from,
            LocalDateTime to,
            List<Long> categories,
            LocalDate today,
            Pageable page);

    /** Đếm sản phẩm theo trạng thái quản lý bằng cùng quy tắc với danh sách. */
    ProductTabSummary managementSummary(Long shop, List<Long> categories, LocalDate today);

    /** Lấy trạng thái, lý do và các thao tác được phép cho danh sách sản phẩm. */
    Map<Long, ProductManagementState> managementStates(
            Long shop, List<Long> ids, List<Long> categories, LocalDate today);

    /** Kiểm tra điều kiện kinh doanh của phân loại; chưa kiểm tra số lượng tồn khả dụng. */
    boolean canSellVariant(Long shop, Long variant, List<Long> categories, LocalDate today);

    /** Đếm các cảnh báo tồn thấp và hạn sử dụng trên toàn cửa hàng. */
    InventoryAttentionSummary inventoryAttentionSummary(Long shop, LocalDate today);
}
