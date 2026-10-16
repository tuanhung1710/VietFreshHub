package VietFreshHub.inventory.util;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

/**
 * Refresh a row after acquiring its lock to avoid using an earlier persistence-context snapshot.
 */
@Component
@RequiredArgsConstructor
public class InventoryLockHelper {

    private final EntityManager entityManager;

    public <T> T refresh(T entity) {
        entityManager.refresh(entity, LockModeType.PESSIMISTIC_WRITE);
        return entity;
    }
}
