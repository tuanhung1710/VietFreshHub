package VietFreshHub.inventory.repository;

import VietFreshHub.inventory.entity.BatchMarkdown;
import VietFreshHub.inventory.enums.MarkdownStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BatchMarkdownRepository extends JpaRepository<BatchMarkdown, Long> {
    List<BatchMarkdown> findByBatchIdInAndStatusOrderByMarkdownIdDesc(
            List<Long> ids, MarkdownStatus status);

    List<BatchMarkdown> findByBatchIdAndStatus(Long id, MarkdownStatus status);
}
