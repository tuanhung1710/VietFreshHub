package VietFreshHub.Inventory.repository;
import VietFreshHub.Inventory.entity.BatchMarkdown;
import VietFreshHub.Inventory.enums.MarkdownStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface BatchMarkdownRepository extends JpaRepository<BatchMarkdown,Long> {
    List<BatchMarkdown> findByBatchIdInAndStatusOrderByMarkdownIdDesc(List<Long> ids, MarkdownStatus status);
    List<BatchMarkdown> findByBatchIdAndStatus(Long id, MarkdownStatus status);
}
