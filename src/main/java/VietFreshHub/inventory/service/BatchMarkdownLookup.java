package VietFreshHub.inventory.service;

import VietFreshHub.inventory.entity.BatchMarkdown;
import VietFreshHub.inventory.entity.InventoryBatch;
import VietFreshHub.inventory.enums.MarkdownStatus;
import VietFreshHub.inventory.repository.BatchMarkdownRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Tải giá giảm đang bật cho một nhóm lô, theo thứ tự mới nhất trước. */
@Component
@RequiredArgsConstructor
public class BatchMarkdownLookup {
    private final BatchMarkdownRepository batchMarkdownRepository;

    /** Tải các đợt giảm giá đang bật của danh sách lô, ưu tiên bản ghi mới nhất. */
    public List<BatchMarkdown> findActive(List<InventoryBatch> batches) {
        List<Long> batchIds = new ArrayList<>();
        for (InventoryBatch batch : batches) {
            batchIds.add(batch.getBatchId());
        }
        if (batchIds.isEmpty()) {
            return List.of();
        }
        return batchMarkdownRepository.findByBatchIdInAndStatusOrderByMarkdownIdDesc(
                batchIds, MarkdownStatus.ACTIVE);
    }
}
