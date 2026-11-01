package VietFreshHub.product.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Thống kê toàn cửa hàng, không phụ thuộc từ khóa tìm kiếm hay trang đang xem. */
@Getter
@AllArgsConstructor
public class ProductTabSummary {
    private long all;
    private long active;
    private long outOfStock;
    private long violation;
    private long hidden;
    private long unavailable;
}
