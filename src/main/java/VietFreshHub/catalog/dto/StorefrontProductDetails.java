package VietFreshHub.catalog.dto;

import java.time.LocalDateTime;
import java.util.List;

/** Ảnh và đánh giá đã duyệt dùng trên trang chi tiết sản phẩm. */
public record StorefrontProductDetails(
        List<String> images, long reviewCount, List<Review> reviews) {
    public record Review(
            Long reviewId, Long reviewerId, String reviewer, int rating, String content, LocalDateTime createdAt) {}
}
