package VietFreshHub.Inventory.service;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
/** A row read before acquiring its lock must be refreshed after the lock is obtained. */
@Service @RequiredArgsConstructor
public class InventoryLockService {
    private final EntityManager entityManager;
    public <T> T refresh(T entity) {
        entityManager.refresh(entity,LockModeType.PESSIMISTIC_WRITE);
        return entity;
    }
}
