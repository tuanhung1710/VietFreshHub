package VietFreshHub.catalog.service;

import VietFreshHub.catalog.dto.CatalogBrowseFilter;
import VietFreshHub.catalog.dto.CatalogProductResponse;
import VietFreshHub.catalog.dto.StorefrontCatalogFilter;
import VietFreshHub.catalog.dto.StorefrontHomeResponse;
import VietFreshHub.catalog.dto.StorefrontOptionsResponse;
import VietFreshHub.catalog.dto.StorefrontProduct;
import VietFreshHub.catalog.dto.StorefrontShopHomeResponse;
import VietFreshHub.catalog.entity.CatalogCategory;
import VietFreshHub.catalog.repository.CatalogDetailRepository;
import VietFreshHub.catalog.repository.StorefrontShopRepository.ShopOption;
import VietFreshHub.common.PageResponse;
import org.springframework.data.domain.Page;
import java.util.List;
import java.util.Optional;

public interface StorefrontService {

    /** Chuẩn hóa bộ lọc và lấy danh sách sản phẩm công khai, có phân trang. */
    Page<CatalogProductResponse> searchProducts(StorefrontCatalogFilter filters, int page, int size);

    /** Tìm sản phẩm trong đúng cửa hàng lấy từ đường dẫn. */
    Page<CatalogProductResponse> searchShopProducts(Long shopId, StorefrontCatalogFilter filters, int page, int size);

    /** Lấy năm sản phẩm bán chạy trên toàn sàn hoặc trong một cửa hàng. */
    List<CatalogProductResponse> topProducts(Long shopId);

    /** Lấy số sản phẩm và hai nhóm sản phẩm trên trang chủ của shop. */
    StorefrontShopHomeResponse shopHome(Long shopId);

    /** Đếm sản phẩm đang được bán của cửa hàng, không áp dụng bộ lọc của khách. */
    long productCount(Long shopId);

    /** Lấy danh mục còn được phép hiển thị trên sàn. */
    List<CatalogCategory> categories();

    /** Lấy các cửa hàng đang hoạt động và mở bán. */
    List<ShopOption> shops();

    /** Lấy các nơi gửi hàng đang có cửa hàng mở bán. */
    List<String> shippingPlaces();

    /** Lấy tên danh mục cho tiêu đề trang danh sách. */
    String categoryName(Long categoryId);

    /** Lấy ảnh và đánh giá đã duyệt của sản phẩm. */
    CatalogDetailRepository.Details productDetails(Long productId);

    /** Lấy địa chỉ cửa hàng dùng trên các trang của khách hàng. */
    Optional<String> shopAddress(Long shopId);

    StorefrontOptionsResponse options();
    StorefrontHomeResponse home(CatalogBrowseFilter paging);
    PageResponse<StorefrontProduct> products(CatalogBrowseFilter filter);
    StorefrontProduct product(Long productId, Long variantId);
}
