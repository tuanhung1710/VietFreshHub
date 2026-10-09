package VietFreshHub.sellerapplication.repository;

import VietFreshHub.sellerapplication.entity.SellerApplicationStatusHistory;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SellerApplicationStatusHistoryRepository
        extends JpaRepository<SellerApplicationStatusHistory, Long> {

    @EntityGraph(attributePaths = "changedBy")
    List<SellerApplicationStatusHistory> findByApplication_ApplicationIdOrderByCreatedAtDesc(Long applicationId);
}
