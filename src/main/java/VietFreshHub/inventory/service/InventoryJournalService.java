package VietFreshHub.inventory.service;

import VietFreshHub.inventory.enums.StockTransactionType;

public interface InventoryJournalService {

    /** Ghi biến động kho và đối tượng tham chiếu; nơi gọi tự cập nhật số lượng thực tế. */
    void stock(
            Long variantId,
            Long batchId,
            StockTransactionType type,
            int quantity,
            Long actorId,
            String reason,
            String referenceType,
            Long referenceId);

    /** Ghi lịch sử hành động với người thực hiện, đối tượng và lý do. */
    void audit(Long actorId, String action, String entityType, Long entityId, String reason);
}
