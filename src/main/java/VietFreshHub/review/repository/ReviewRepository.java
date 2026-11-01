package VietFreshHub.review.repository;

import VietFreshHub.review.entity.Review;
import VietFreshHub.review.entity.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    interface RatingSummary {
        Double getAverageRating();
        Long getRatingCount();
    }

    @EntityGraph(attributePaths = {"user", "product", "shop"})
    Page<Review> findByProduct_ProductIdAndStatusOrderByCreatedAtDesc(Long productId, ReviewStatus status,
                                                                       Pageable pageable);
    @EntityGraph(attributePaths = {"user", "product", "shop"})
    Page<Review> findByShop_ShopIdAndStatusOrderByCreatedAtDesc(Long shopId, ReviewStatus status,
                                                                 Pageable pageable);
    @EntityGraph(attributePaths = {"user", "product", "shop"})
    Page<Review> findByStatusOrderByCreatedAtAsc(ReviewStatus status, Pageable pageable);
    @EntityGraph(attributePaths = {"user", "product", "shop"})
    Page<Review> findByUser_UserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    List<Review> findByUser_UserIdAndOrderItem_OrderItemIdIn(Long userId, Collection<Long> itemIds);
    List<Review> findByUser_UserIdAndShopOrder_ShopOrderIdIn(Long userId, Collection<Long> shopOrderIds);
    Optional<Review> findByReviewIdAndUser_UserId(Long reviewId, Long userId);
    boolean existsByUser_UserIdAndOrderItem_OrderItemId(Long userId, Long orderItemId);
    boolean existsByUser_UserIdAndShopOrder_ShopOrderId(Long userId, Long shopOrderId);
    @Query("select avg(r.rating) as averageRating, count(r) as ratingCount from Review r "
            + "where r.product.productId = :productId and r.status = :status")
    RatingSummary productRating(@Param("productId") Long productId, @Param("status") ReviewStatus status);

    @Query("select avg(r.rating) as averageRating, count(r) as ratingCount from Review r "
            + "where r.shop.shopId = :shopId and r.status = :status")
    RatingSummary shopRating(@Param("shopId") Long shopId, @Param("status") ReviewStatus status);
}
