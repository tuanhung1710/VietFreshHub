package VietFreshHub.Inventory.service;
import VietFreshHub.Inventory.entity.*;
import VietFreshHub.Inventory.enums.StockTransactionType;
import VietFreshHub.Inventory.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service @RequiredArgsConstructor
public class InventoryJournalService {
    private final InventoryTransactionRepository transactions;
    private final AuditEventRepository audits;
    @Transactional
    public void stock(Long variant,Long batch,StockTransactionType type,int quantity,Long actor,String reason,String referenceType,Long reference) {
        InventoryRules.require(type!=null,"Chọn loại giao dịch kho.");
        InventoryRules.require(quantity!=0,"Không ghi giao dịch số lượng 0.");
        InventoryTransaction t=new InventoryTransaction();
        t.setVariantId(variant); t.setBatchId(batch); t.setTransactionType(type); t.setQuantity(quantity);
        t.setCreatedBy(actor); t.setNote(reason); t.setCreatedAt(InventoryRules.now());
        t.setReferenceType(referenceType); t.setReferenceId(reference); transactions.save(t);
    }
    @Transactional
    public void audit(Long actor,String action,String type,Long id,String reason) {
        AuditEvent a=new AuditEvent(); a.setActorId(actor); a.setAction(action); a.setEntityType(type);
        a.setEntityId(id); a.setReason(reason.length()>1000?reason.substring(0,997)+"...":reason); a.setCreatedAt(InventoryRules.now()); audits.save(a);
    }
}
