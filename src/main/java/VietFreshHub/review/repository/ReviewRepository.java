package VietFreshHub.review.repository;

import VietFreshHub.review.entity.Review;
import VietFreshHub.review.entity.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    interface ProductOption { Long getId(); String getName(); }
    interface RatingSummary {
        Double getAverageRating();
        Long getRatingCount();
    }

    @EntityGraph(attributePaths = {"user", "product", "shop"})
    Page<Review> findByProduct_ProductIdAndStatusOrderByCreatedAtDesc(Long productId, ReviewStatus status,
                                                                       Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Review r where r.reviewId = :reviewId")
    Optional<Review> findByReviewIdForUpdate(@Param("reviewId") Long reviewId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Review r where r.reviewId = :reviewId and r.user.userId = :userId")
    Optional<Review> findOwnedReviewForUpdate(@Param("reviewId") Long reviewId, @Param("userId") Long userId);
    @EntityGraph(attributePaths = {"user", "product", "shop"})
    @Query("select r from Review r where r.product.productId = :productId and r.status = :status "
            + "and (:rating is null or r.rating = :rating)")
    Page<Review> findProductReviews(@Param("productId") Long productId, @Param("status") ReviewStatus status,
                                    @Param("rating") Byte rating, Pageable pageable);
    @EntityGraph(attributePaths = {"user", "product", "shop"})
    Page<Review> findByShop_ShopIdAndStatusOrderByCreatedAtDesc(Long shopId, ReviewStatus status,
                                                                 Pageable pageable);
    @EntityGraph(attributePaths = {"user", "product", "product.shop", "shop", "shopRepliedBy"})
    Page<Review> findByProduct_Shop_ShopIdAndStatusOrderByCreatedAtDesc(
            Long shopId, ReviewStatus status, Pageable pageable);
    @EntityGraph(attributePaths = {"user", "product", "shop"})
    Page<Review> findByUser_UserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    @EntityGraph(attributePaths = {"user", "product", "shop"})
    @Query("select r from Review r where r.user.userId = :userId and (:rating is null or r.rating = :rating) "
            + "and (:productId is null or r.product.productId = :productId)")
    Page<Review> findMyReviews(@Param("userId") Long userId, @Param("rating") Byte rating,
                               @Param("productId") Long productId, Pageable pageable);
    @Query("select distinct p.productId as id, p.name as name from Review r join r.product p where r.user.userId = :userId order by p.name")
    List<ProductOption> findReviewedProducts(@Param("userId") Long userId);
    List<Review> findByUser_UserIdAndOrderItem_OrderItemIdIn(Long userId, Collection<Long> itemIds);
    List<Review> findByUser_UserIdAndShopOrder_ShopOrderIdIn(Long userId, Collection<Long> shopOrderIds);
    Optional<Review> findByReviewIdAndUser_UserId(Long reviewId, Long userId);
    boolean existsByUser_UserIdAndOrderItem_OrderItemId(Long userId, Long orderItemId);
    @Query("select avg(r.rating) as averageRating, count(r) as ratingCount from Review r "
            + "where r.product.productId = :productId and r.status = :status")
    RatingSummary productRating(@Param("productId") Long productId, @Param("status") ReviewStatus status);

    @Query("select avg(r.rating) as averageRating, count(r) as ratingCount from Review r "
            + "where r.shop.shopId = :shopId and r.status = :status")
    RatingSummary shopRating(@Param("shopId") Long shopId, @Param("status") ReviewStatus status);
}
