package VietFreshHub.inventory.service;

import VietFreshHub.inventory.dto.BatchRequest;
import VietFreshHub.inventory.dto.ActivityHistoryFilter;
import VietFreshHub.inventory.dto.AuditResponse;
import VietFreshHub.inventory.dto.BatchResponse;
import VietFreshHub.inventory.dto.JournalResponse;
import VietFreshHub.inventory.dto.MarkdownRequest;
import VietFreshHub.inventory.dto.StockChangeRequest;
import VietFreshHub.inventory.entity.AuditEvent;

import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;

import java.time.LocalDate;

public interface InventoryService {
    String batchCode(Long batchId);

    void restock(Authentication authentication, Long variantId, BatchRequest form);

    BatchRequest batchForm(Authentication authentication, Long batchId);

    void updateBatch(Authentication authentication, Long batchId, BatchRequest form);

    void adjust(Authentication authentication, Long batchId, StockChangeRequest form);

    void dispose(Authentication authentication, Long batchId, StockChangeRequest form);

    void block(Authentication authentication, Long batchId, boolean blocked, String reason);

    void markdown(Authentication authentication, Long batchId, MarkdownRequest form);

    void cancelMarkdown(Authentication authentication, Long batchId, String reason);

    Page<JournalResponse> journal(
            Authentication authentication,
            Long variantId,
            String type,
            String query,
            LocalDate from,
            LocalDate to,
            int page,
            int size);

    Page<JournalResponse> journal(
            Authentication authentication,
            Long variantId,
            Long batchId,
            String type,
            String query,
            LocalDate from,
            LocalDate to,
            int page,
            int size);

    Page<AuditEvent> audit(Authentication authentication, int page, int size);

    Page<AuditResponse> activityHistory(Authentication authentication, int page, int size);

    Page<AuditResponse> activityHistory(Authentication authentication, ActivityHistoryFilter filters, int page, int size);

    BatchResponse batch(Authentication authentication, Long batchId);
}
