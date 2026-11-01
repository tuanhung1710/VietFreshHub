package VietFreshHub.inventory.util;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

/**
 * Khóa và tải lại bản ghi để các thao tác kho dùng dữ liệu mới nhất trong cùng giao dịch.
 */
@Component
@RequiredArgsConstructor
public class InventoryLockHelper {

    private final EntityManager entityManager;

    /** Khóa ghi và tải lại bản ghi từ cơ sở dữ liệu, tránh dùng dữ liệu cũ trong ngữ cảnh JPA. */
    public <T> T refresh(T entity) {
        entityManager.refresh(entity, LockModeType.PESSIMISTIC_WRITE);
        return entity;
    }
}
