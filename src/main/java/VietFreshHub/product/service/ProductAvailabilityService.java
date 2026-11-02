package VietFreshHub.product.service;

import VietFreshHub.product.dto.ProductManagementState;
import VietFreshHub.product.entity.ProductVariant;

import java.util.List;
import java.util.Map;

public interface ProductAvailabilityService {

    /** Lấy ID danh mục có toàn bộ chuỗi danh mục cha còn hoạt động. */
    List<Long> activeCategoryIds();

    /** Lấy trạng thái quản lý nhiều sản phẩm theo điều kiện kinh doanh hiện tại. */
    Map<Long, ProductManagementState> states(Long shopId, List<Long> productIds);

    /** Kiểm tra điều kiện kinh doanh của phân loại; số lượng tồn được xét riêng khi giữ hàng. */
    boolean canSellVariant(Long shopId, ProductVariant variant);
}
