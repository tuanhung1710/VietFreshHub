package VietFreshHub.product.dto;

import VietFreshHub.product.enums.ProductStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Nội dung xác nhận thao tác trên một sản phẩm, lấy từ dữ liệu hiện tại. */
@Getter
@RequiredArgsConstructor
public class ProductActionReview {
    private final ProductStatus status;
    private final ProductResponse row;

    public String getLabel() {
        return switch (status) {
            case ACTIVE -> "Mở bán";
            case INACTIVE -> "Tạm ẩn";
            case DELETED -> "Xóa sản phẩm";
            default -> throw new IllegalStateException("Thao tác không được hỗ trợ.");
        };
    }

    public String getDescription() {
        return switch (status) {
            case ACTIVE -> "Sản phẩm được mở bán khi đáp ứng đầy đủ điều kiện kinh doanh.";
            case INACTIVE -> "Sản phẩm sẽ được tạm ẩn khỏi cửa hàng và có thể mở bán lại khi đủ điều kiện.";
            case DELETED -> "Sản phẩm và các phân loại sẽ được xóa khỏi danh sách quản lý. "
                    + "Tồn kho và lịch sử vẫn được giữ lại trong Kho hàng. Hiện chưa hỗ trợ khôi phục. "
                    + "Cần xử lý hết số lượng chờ xuất kho trước khi xóa.";
            default -> throw new IllegalStateException("Thao tác không được hỗ trợ.");
        };
    }
}
