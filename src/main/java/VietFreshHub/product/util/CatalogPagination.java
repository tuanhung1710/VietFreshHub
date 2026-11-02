package VietFreshHub.product.util;

import VietFreshHub.common.Pagination;
import org.springframework.data.domain.PageRequest;

/** Phân trang danh mục bắt đầu từ 1; giới hạn offset trong miền int của truy vấn JPA. */
public final class CatalogPagination {
    private CatalogPagination() {}

    public static PageRequest request(int requestedPage, int requestedSize) {
        return Pagination.request(requestedPage, requestedSize);
    }
}
