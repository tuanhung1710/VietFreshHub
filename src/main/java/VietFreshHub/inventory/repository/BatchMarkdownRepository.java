package VietFreshHub.inventory.repository;

import VietFreshHub.inventory.entity.BatchMarkdown;
import VietFreshHub.inventory.enums.MarkdownStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BatchMarkdownRepository extends JpaRepository<BatchMarkdown, Long> {
    /** Tải giảm giá của nhiều lô theo trạng thái, ưu tiên bản ghi mới nhất. */
    List<BatchMarkdown> findByBatchIdInAndStatusOrderByMarkdownIdDesc(
            List<Long> ids, MarkdownStatus status);

    /** Lấy các đợt giảm giá của một lô theo trạng thái chỉ định. */
    List<BatchMarkdown> findByBatchIdAndStatus(Long id, MarkdownStatus status);
}
