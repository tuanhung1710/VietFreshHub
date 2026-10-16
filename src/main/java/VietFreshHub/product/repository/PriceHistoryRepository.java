package VietFreshHub.product.repository;

import VietFreshHub.product.entity.PriceHistory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PriceHistoryRepository extends JpaRepository<PriceHistory, Long> {
    List<PriceHistory> findByVariantIdAndEffectiveToIsNull(Long variantId);

    Page<PriceHistory> findByVariantIdOrderByEffectiveFromDescPriceHistoryIdDesc(
            Long variantId, Pageable page);
}
