package VietFreshHub.Inventory.service;
import VietFreshHub.Inventory.dto.*;
import VietFreshHub.Inventory.entity.AuditEvent;
import org.springframework.security.core.Authentication;
import org.springframework.data.domain.Page;
import java.time.LocalDate;
public interface InventoryService {
    void restock(Authentication auth,Long variant,BatchRequest form);
    BatchRequest batchForm(Authentication auth,Long batch);
    void updateBatch(Authentication auth,Long batch,BatchRequest form);
    void adjust(Authentication auth,Long batch,StockChangeRequest form);
    void dispose(Authentication auth,Long batch,StockChangeRequest form);
    void block(Authentication auth,Long batch,boolean blocked,String reason);
    void markdown(Authentication auth,Long batch,MarkdownRequest form);
    void cancelMarkdown(Authentication auth,Long batch,String reason);
    Page<JournalResponse> journal(Authentication auth,Long variant,String type,String q,LocalDate from,LocalDate to,int page,int size);
    Page<AuditEvent> audit(Authentication auth,int page,int size);
    BatchResponse batch(Authentication auth,Long batch);
}
