package VietFreshHub.inventory.repository;

import VietFreshHub.inventory.entity.InventoryOrderLine;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InventoryOrderLineRepository extends JpaRepository<InventoryOrderLine, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from InventoryOrderLine l where l.orderItemId=:id")
    Optional<InventoryOrderLine> lockById(Long id);
}
