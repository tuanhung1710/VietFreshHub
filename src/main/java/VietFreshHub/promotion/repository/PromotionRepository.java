package VietFreshHub.promotion.repository;

import VietFreshHub.promotion.entity.Promotion;
import VietFreshHub.promotion.entity.PromotionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PromotionRepository extends JpaRepository<Promotion, Long> {

    @Query("""
            select p.promotionId
            from Promotion p
            where p.status = :status
              and p.shopId is not null
              and p.startAt <= :now
              and p.endAt > :now
            order by p.promotionId
            """)
    List<Long> findOngoingPromotionIds(
            @Param("status") PromotionStatus status,
            @Param("now") LocalDateTime now
    );

    @Query(value = """
            SELECT f.user_id
            FROM dbo.favorite_shops f
            JOIN dbo.users u ON u.user_id = f.user_id
            WHERE f.shop_id = :shopId
              AND u.status = 'ACTIVE'
            ORDER BY f.user_id
            """, nativeQuery = true)
    List<Long> findFavoriteShopUserIds(@Param("shopId") Long shopId);

    List<Promotion> findByShopIdOrderByCreatedAtDesc(Long shopId);

    Optional<Promotion> findByPromotionIdAndShopId(Long promotionId, Long shopId);
}
