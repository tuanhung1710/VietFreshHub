package VietFreshHub.promotion.repository;

import VietFreshHub.promotion.entity.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PromotionRepository extends JpaRepository<Promotion, Long> {

    List<Promotion> findByShopIdOrderByCreatedAtDesc(Long shopId);

    Optional<Promotion> findByPromotionIdAndShopId(Long promotionId, Long shopId);
}
