package VietFreshHub.common;

import org.springframework.data.domain.PageRequest;

/** Quy ước số trang bắt đầu từ 1, dùng chung cho các danh sách của hệ thống. */
public final class Pagination {
    public static final int MAX_PAGE = 10000;

    private Pagination() { }

    public static PageRequest request(int requestedPage, int requestedSize) {
        int size = PageSizes.normalize(requestedSize);
        int index = Math.min(Math.max(1, requestedPage) - 1, Integer.MAX_VALUE / size);
        return PageRequest.of(index, size);
    }

    public static PageRequest clamp(int requestedPage, int requestedSize, long total) {
        int size = PageSizes.normalize(requestedSize);
        long lastPage = total > 0 ? (total - 1) / size + 1 : 1;
        return request((int) Math.min(Math.max(1, requestedPage), lastPage), size);
    }
}
