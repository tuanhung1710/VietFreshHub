package VietFreshHub.Product.repository;
import VietFreshHub.Product.entity.PriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import org.springframework.data.domain.*;
public interface PriceHistoryRepository extends JpaRepository<PriceHistory,Long> {
    List<PriceHistory> findByVariantIdAndEffectiveToIsNull(Long variantId);
    Page<PriceHistory> findByVariantIdOrderByEffectiveFromDescPriceHistoryIdDesc(Long variantId,Pageable page);
}
