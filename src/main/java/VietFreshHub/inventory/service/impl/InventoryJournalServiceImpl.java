package VietFreshHub.inventory.service.impl;

import VietFreshHub.inventory.entity.AuditEvent;
import VietFreshHub.inventory.entity.InventoryTransaction;
import VietFreshHub.inventory.enums.StockTransactionType;
import VietFreshHub.inventory.repository.AuditEventRepository;
import VietFreshHub.inventory.repository.InventoryTransactionRepository;
import VietFreshHub.inventory.rule.InventoryRules;
import VietFreshHub.inventory.service.InventoryJournalService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventoryJournalServiceImpl implements InventoryJournalService {

    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final AuditEventRepository auditEventRepository;

    @Override
    @Transactional
    public void stock(
            Long variantId,
            Long batchId,
            StockTransactionType type,
            int quantity,
            Long actorId,
            String reason,
            String referenceType,
            Long referenceId) {
        InventoryRules.require(type != null, "Vui lòng chọn loại biến động kho.");
        InventoryRules.require(quantity != 0, "Số lượng thay đổi phải khác 0.");

        InventoryTransaction transaction = new InventoryTransaction();
        transaction.setVariantId(variantId);
        transaction.setBatchId(batchId);
        transaction.setTransactionType(type);
        transaction.setQuantity(quantity);
        transaction.setReferenceType(referenceType);
        transaction.setReferenceId(referenceId);
        transaction.setCreatedBy(actorId);
        transaction.setNote(reason);
        transaction.setCreatedAt(InventoryRules.now());
        inventoryTransactionRepository.save(transaction);
    }

    @Override
    @Transactional
    public void audit(
            Long actorId, String action, String entityType, Long entityId, String reason) {
        String auditReason = reason;
        if (auditReason.length() > 1000) {
            auditReason = auditReason.substring(0, 997) + "...";
        }

        AuditEvent event = new AuditEvent();
        event.setActorId(actorId);
        event.setAction(action);
        event.setEntityType(entityType);
        event.setEntityId(entityId);
        event.setReason(auditReason);
        event.setCreatedAt(InventoryRules.now());
        auditEventRepository.save(event);
    }
}
