package VietFreshHub.Inventory.repository;
import VietFreshHub.Inventory.entity.InventoryOrderLine;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;
public interface InventoryOrderLineRepository extends JpaRepository<InventoryOrderLine,Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from InventoryOrderLine l where l.orderItemId=:id")
    Optional<InventoryOrderLine> lockById(Long id);
}
