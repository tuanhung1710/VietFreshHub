package VietFreshHub.product.repository;

import VietFreshHub.product.entity.PriceHistory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PriceHistoryRepository extends JpaRepository<PriceHistory, Long> {
    /** Lấy các khoảng giá chưa kết thúc của phân loại để đóng khi ghi giá mới. */
    List<PriceHistory> findByVariantIdAndEffectiveToIsNull(Long variantId);

    /** Lấy lịch sử giá mới nhất trước, dùng ID phân biệt các bản ghi cùng thời điểm. */
    Page<PriceHistory> findByVariantIdOrderByEffectiveFromDescPriceHistoryIdDesc(
            Long variantId, Pageable page);
}
