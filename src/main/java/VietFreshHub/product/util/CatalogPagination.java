package VietFreshHub.product.util;

import VietFreshHub.common.PageSizes;
import org.springframework.data.domain.PageRequest;

/** Phân trang danh mục bắt đầu từ 1; giới hạn offset trong miền int của truy vấn JPA. */
public final class CatalogPagination {
    private CatalogPagination() {}

    public static PageRequest request(int requestedPage, int requestedSize) {
        int pageSize = PageSizes.normalize(requestedSize);
        int requestedPageIndex = Math.max(1, requestedPage) - 1;
        int maximumSafePageIndex = Integer.MAX_VALUE / pageSize;
        int pageIndex = Math.min(requestedPageIndex, maximumSafePageIndex);
        return PageRequest.of(pageIndex, pageSize);
    }
}
