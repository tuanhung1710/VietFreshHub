package VietFreshHub.product.service;

import static VietFreshHub.inventory.rule.InventoryRules.today;

import VietFreshHub.common.Pagination;
import VietFreshHub.product.dto.ProductCatalogFilter;
import VietFreshHub.product.dto.ProductResponse;
import VietFreshHub.product.entity.Product;
import VietFreshHub.product.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;

/** Thực hiện truy vấn và dựng danh sách sau khi service đã xác thực shop, bộ lọc. */
@Component
@RequiredArgsConstructor
public class ProductCatalogQueries {
    private final ProductRepository productRepository;
    private final ProductCatalogReader catalogReader;

    public Page<ProductResponse> searchManagement(
            Long shopId,
            ProductCatalogFilter filters,
            List<Long> activeCategories,
            int page,
            int size) {
//        Tạo yêu cầu phân trang
        PageRequest pageable = Pagination.request(page, size);

// Lấy trang đang được yêu cầu, chứa danh sách sản phẩm và thông tin trang
        Page<Product> result =
                productRepository.searchManagement(
                        shopId,
                        filters,
                        activeCategories,
                        today(),
                        pageable);
// result.getNumber() chính làchirir số trang đang yêu cầu
        if (result.getNumber() >= Math.max(1, result.getTotalPages())) {
            // Đưa về trang đầu khi trang cũ không còn tồn tại, chẳng hạn sau khi đổi bộ lọc.
            result =
                    productRepository.searchManagement(
                            shopId,
                            filters,
                            activeCategories,
                            today(),
                            pageable.withPage(0));
        }

        // Đóng vai trò kiểm tra xem tại trang tìm kiếm thì liệu còn hợp lệ hay không
        // Chẳng hạn như nếu đang ở trang thứ 3 trong khi đó kết quả tìm kiếm trả về có hai
        // thì chứng tỏ trang hiện tại không hợp lệ, pageAble sẽ phải chuyển về tragn đầu tiền rồi hin thị các kết quả
//        Chuyển dữ liệu trả về.
        return catalogReader.toPage(result);
    }
}
