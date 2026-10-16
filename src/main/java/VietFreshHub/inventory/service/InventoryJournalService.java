package VietFreshHub.inventory.service;

import VietFreshHub.inventory.enums.StockTransactionType;

public interface InventoryJournalService {

    void stock(
            Long variantId,
            Long batchId,
            StockTransactionType type,
            int quantity,
            Long actorId,
            String reason,
            String referenceType,
            Long referenceId);

    void audit(Long actorId, String action, String entityType, Long entityId, String reason);
}
