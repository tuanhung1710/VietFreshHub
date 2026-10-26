package VietFreshHub.inventory.service.impl;

import static VietFreshHub.inventory.rule.InventoryRules.available;
import static VietFreshHub.inventory.rule.InventoryRules.expired;
import static VietFreshHub.inventory.rule.InventoryRules.now;
import static VietFreshHub.inventory.rule.InventoryRules.reason;
import static VietFreshHub.inventory.rule.InventoryRules.require;
import static VietFreshHub.inventory.rule.InventoryRules.today;

import VietFreshHub.auth.service.AuthService;
import VietFreshHub.inventory.entity.BatchMarkdown;
import VietFreshHub.inventory.entity.InventoryBatch;
import VietFreshHub.inventory.entity.InventoryOrderLine;
import VietFreshHub.inventory.entity.StockAllocation;
import VietFreshHub.inventory.enums.AllocationStatus;
import VietFreshHub.inventory.enums.BatchStatus;
import VietFreshHub.inventory.enums.MarkdownStatus;
import VietFreshHub.inventory.enums.StockTransactionType;
import VietFreshHub.inventory.repository.BatchMarkdownRepository;
import VietFreshHub.inventory.repository.InventoryBatchRepository;
import VietFreshHub.inventory.repository.InventoryOrderLineRepository;
import VietFreshHub.inventory.repository.StockAllocationRepository;
import VietFreshHub.inventory.rule.InventoryRules;
import VietFreshHub.inventory.service.InventoryJournalService;
import VietFreshHub.inventory.service.StockAllocationService;
import VietFreshHub.inventory.util.InventoryLockHelper;
import VietFreshHub.order.entity.ShopOrder;
import VietFreshHub.order.repository.ShopOrderRepository;
import VietFreshHub.product.entity.Product;
import VietFreshHub.product.entity.ProductVariant;
import VietFreshHub.product.repository.ProductRepository;
import VietFreshHub.product.repository.ProductVariantRepository;
import VietFreshHub.product.service.ProductAvailabilityService;
import VietFreshHub.shop.service.ShopService;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class StockAllocationServiceImpl implements StockAllocationService {

    private final InventoryOrderLineRepository inventoryOrderLineRepository;
    private final ShopOrderRepository shopOrderRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryBatchRepository inventoryBatchRepository;
    private final StockAllocationRepository stockAllocationRepository;
    private final BatchMarkdownRepository batchMarkdownRepository;
    private final ShopService shopService;
    private final AuthService authService;
    private final InventoryJournalService inventoryJournalService;
    private final InventoryLockHelper inventoryLockHelper;
    private final ProductAvailabilityService productAvailabilityService;

    @Override
    public List<Long> reserve(Authentication authentication, Long orderItemId) {
        InventoryOrderLine line = getOwnedOrderLineForUpdate(authentication, orderItemId);
        ShopOrder order = shopOrderRepository.findById(line.getShopOrderId()).orElseThrow();
        require(
                List.of("CONFIRMED", "PREPARING").contains(order.getStatus()),
                "Chỉ được giữ hàng sau khi người bán xác nhận đơn hàng.");

        List<StockAllocation> existing =
                stockAllocationRepository.findByOrderItemIdOrderByAllocationId(orderItemId);
        if (!existing.isEmpty()) {
            return existingReservationIds(existing, line.getQuantity());
        }

        ProductVariant variant =
                productVariantRepository.findById(line.getVariantId()).orElseThrow();
        validateSellableVariant(variant);
        LocalDate date = today();
        List<InventoryBatch> candidates = lockAvailableBatches(variant.getVariantId(), date);
        List<BatchMarkdown> markdowns = findActiveMarkdowns(candidates);
        BigDecimal minimumPrice =
                lowestAvailablePrice(candidates, markdowns, variant.getPrice(), date);
        require(
                line.getUnitPrice().compareTo(minimumPrice) == 0,
                "Giá sản phẩm đã thay đổi. Vui lòng tải lại để cập nhật giá.");

        if (minimumPrice.compareTo(variant.getPrice()) < 0) {
            candidates =
                    batchesAtMarkdownPrice(
                            candidates, markdowns, variant.getPrice(), minimumPrice, date);
        }
        require(
                line.getQuantity() != null && line.getQuantity() > 0,
                "Số lượng sản phẩm trong đơn phải lớn hơn 0.");
        require(
                totalAvailable(candidates, date) >= line.getQuantity(),
                "Không đủ số lượng khả dụng ở mức giá đặt hàng.");

        Long actorId = authService.getCurrentUserId(authentication);
        return allocateStock(line, candidates, actorId, date);
    }

    private List<Long> existingReservationIds(
            List<StockAllocation> allocations, int expectedQuantity) {
        long reservedQuantity = 0;
        List<Long> allocationIds = new ArrayList<>();
        boolean allReserved = true;
        for (StockAllocation allocation : allocations) {
            if (allocation.getStatus() != AllocationStatus.RESERVED) {
                allReserved = false;
            }
            reservedQuantity += allocation.getQuantity();
            allocationIds.add(allocation.getAllocationId());
        }
        require(
                allReserved && reservedQuantity == expectedQuantity,
                "Sản phẩm trong đơn đã được xuất bán, giải phóng hàng giữ hoặc thay đổi số lượng. Không thể tự động giữ hàng lại.");
        return allocationIds;
    }

    private void validateSellableVariant(ProductVariant variant) {
        // getOwnedOrderLineForUpdate already holds the product and variant locks.
        Product product = productRepository.findById(variant.getProductId()).orElseThrow();
        boolean sellable = productAvailabilityService.canSellVariant(product.getShopId(), variant);
        require(sellable, "Sản phẩm hoặc phân loại hiện không được mở bán.");
    }

    private List<InventoryBatch> lockAvailableBatches(Long variantId, LocalDate date) {
        List<InventoryBatch> candidates = new ArrayList<>();
        List<InventoryBatch> snapshots =
                inventoryBatchRepository.findByVariantIdOrderByExpiryDateAscBatchIdAsc(variantId);
        for (InventoryBatch snapshot : snapshots.stream()
                .sorted(Comparator.comparing(InventoryBatch::getBatchId)).toList()) {
            InventoryBatch batch = getBatchForUpdate(snapshot.getBatchId());
            if (available(batch, date) <= 0) {
                continue;
            }
            candidates.add(batch);
        }
        // Legacy lots without expiry are used last. Equal dates are ordered by numeric batch ID.
        Comparator<LocalDate> expiryOrder = Comparator.nullsLast(Comparator.naturalOrder());
        Comparator<InventoryBatch> fefoOrder =
                Comparator.comparing(InventoryBatch::getExpiryDate, expiryOrder);
        candidates.sort(fefoOrder.thenComparing(InventoryBatch::getBatchId));
        return candidates;
    }

    private List<BatchMarkdown> findActiveMarkdowns(List<InventoryBatch> batches) {
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

    private BigDecimal lowestAvailablePrice(
            List<InventoryBatch> batches,
            List<BatchMarkdown> markdowns,
            BigDecimal basePrice,
            LocalDate date) {
        BigDecimal minimumPrice = basePrice;
        for (InventoryBatch batch : batches) {
            for (BatchMarkdown markdown : markdowns) {
                if (!InventoryRules.isApplicableMarkdown(batch, markdown, basePrice, date)) {
                    continue;
                }
                if (markdown.getPrice().compareTo(minimumPrice) < 0) {
                    minimumPrice = markdown.getPrice();
                }
            }
        }
        return minimumPrice;
    }

    private List<InventoryBatch> batchesAtMarkdownPrice(
            List<InventoryBatch> batches,
            List<BatchMarkdown> markdowns,
            BigDecimal basePrice,
            BigDecimal price,
            LocalDate date) {
        List<InventoryBatch> matchingBatches = new ArrayList<>();
        for (InventoryBatch batch : batches) {
            for (BatchMarkdown markdown : markdowns) {
                if (markdown.getPrice().compareTo(price) == 0
                        && InventoryRules.isApplicableMarkdown(batch, markdown, basePrice, date)) {
                    matchingBatches.add(batch);
                    break;
                }
            }
        }
        return matchingBatches;
    }

    private long totalAvailable(List<InventoryBatch> batches, LocalDate date) {
        long total = 0;
        for (InventoryBatch batch : batches) {
            total += available(batch, date);
        }
        return total;
    }

    private List<Long> allocateStock(
            InventoryOrderLine line, List<InventoryBatch> batches, Long actorId, LocalDate date) {
        int neededQuantity = line.getQuantity();
        List<Long> allocationIds = new ArrayList<>();
        for (InventoryBatch batch : batches) {
            int quantity = Math.min(neededQuantity, available(batch, date));
            if (quantity == 0) {
                continue;
            }
            StockAllocation allocation =
                    reserveBatch(line.getOrderItemId(), batch, quantity, actorId);
            allocationIds.add(allocation.getAllocationId());
            neededQuantity -= quantity;
            if (neededQuantity == 0) {
                break;
            }
        }
        return allocationIds;
    }

    private StockAllocation reserveBatch(
            Long orderItemId, InventoryBatch batch, int quantity, Long actorId) {
        batch.setReservedQuantity(batch.getReservedQuantity() + quantity);
        batch.setUpdatedAt(now());
        inventoryBatchRepository.save(batch);

        StockAllocation allocation = new StockAllocation();
        allocation.setOrderItemId(orderItemId);
        allocation.setBatchId(batch.getBatchId());
        allocation.setQuantity(quantity);
        allocation.setStatus(AllocationStatus.RESERVED);
        stockAllocationRepository.saveAndFlush(allocation);

        inventoryJournalService.stock(
                batch.getVariantId(),
                batch.getBatchId(),
                StockTransactionType.RESERVE,
                quantity,
                actorId,
                "Giữ hàng từ lô hết hạn sớm nhất.",
                "ORDER_ITEM",
                orderItemId);
        inventoryJournalService.audit(
                actorId,
                "RESERVE",
                "BATCH",
                batch.getBatchId(),
                "Đã giữ " + quantity + " cho dòng sản phẩm trong đơn #" + orderItemId + ".");
        return allocation;
    }

    @Override
    public void release(Authentication authentication, Long orderItemId, String value) {
        getOwnedOrderLineForUpdate(authentication, orderItemId);
        String releaseReason = reason(value);
        Long actorId = authService.getCurrentUserId(authentication);
        List<StockAllocation> allocations =
                stockAllocationRepository.findByOrderItemIdOrderByAllocationId(orderItemId);
        for (StockAllocation allocation : allocations) {
            if (allocation.getStatus() != AllocationStatus.RESERVED) {
                continue;
            }
            releaseBatch(allocation, actorId, releaseReason);
        }
    }

    private void releaseBatch(StockAllocation allocation, Long actorId, String releaseReason) {
        InventoryBatch batch = getBatchForUpdate(allocation.getBatchId());
        int quantity = allocation.getQuantity();
        require(
                batch.getReservedQuantity() >= quantity,
                "Số lượng đã giữ cho đơn không khớp với số lượng phân bổ của lô.");
        batch.setReservedQuantity(batch.getReservedQuantity() - quantity);
        batch.setUpdatedAt(now());
        inventoryBatchRepository.save(batch);
        allocation.setStatus(AllocationStatus.RELEASED);
        stockAllocationRepository.save(allocation);

        Long orderItemId = allocation.getOrderItemId();
        inventoryJournalService.stock(
                batch.getVariantId(),
                batch.getBatchId(),
                StockTransactionType.RELEASE,
                -quantity,
                actorId,
                releaseReason,
                "ORDER_ITEM",
                orderItemId);
        inventoryJournalService.audit(
                actorId,
                "RELEASE",
                "BATCH",
                batch.getBatchId(),
                releaseReason + "; giải phóng " + quantity + " hàng đã giữ cho dòng sản phẩm trong đơn #" + orderItemId);
    }

    @Override
    public void sell(Authentication authentication, Long orderItemId) {
        InventoryOrderLine line = getOwnedOrderLineForUpdate(authentication, orderItemId);
        Long actorId = authService.getCurrentUserId(authentication);
        List<StockAllocation> allocations =
                stockAllocationRepository.findByOrderItemIdOrderByAllocationId(orderItemId);
        ShopOrder order = shopOrderRepository.findById(line.getShopOrderId()).orElseThrow();
        require(
                List.of("READY_FOR_DELIVERY", "OUT_FOR_DELIVERY", "COMPLETED")
                        .contains(order.getStatus()),
                "Chỉ ghi nhận xuất bán khi đơn hàng đã sẵn sàng giao, đang giao hoặc đã hoàn tất.");

        validateCompleteAllocation(allocations, line.getQuantity());
        if (allAllocationsHaveStatus(allocations, AllocationStatus.SOLD)) {
            return;
        }
        require(
                allAllocationsHaveStatus(allocations, AllocationStatus.RESERVED),
                "Trạng thái phân bổ hàng không hợp lệ. Vui lòng kiểm tra lại đơn hàng.");
        for (StockAllocation allocation : allocations) {
            sellBatch(allocation, actorId);
        }
    }

    private void validateCompleteAllocation(
            List<StockAllocation> allocations, int expectedQuantity) {
        long quantity = 0;
        boolean hasReleased = false;
        for (StockAllocation allocation : allocations) {
            quantity += allocation.getQuantity();
            if (allocation.getStatus() == AllocationStatus.RELEASED) {
                hasReleased = true;
            }
        }
        require(
                !allocations.isEmpty() && !hasReleased && quantity == expectedQuantity,
                "Phải giữ đủ hàng cho sản phẩm trong đơn trước khi ghi nhận xuất bán.");
    }

    private boolean allAllocationsHaveStatus(
            List<StockAllocation> allocations, AllocationStatus status) {
        for (StockAllocation allocation : allocations) {
            if (allocation.getStatus() != status) {
                return false;
            }
        }
        return true;
    }

    private void sellBatch(StockAllocation allocation, Long actorId) {
        InventoryBatch batch = getBatchForUpdate(allocation.getBatchId());
        int quantity = allocation.getQuantity();
        require(
                !expired(batch, today()) && batch.getStatus() == BatchStatus.ACTIVE,
                "Lô hàng đã giữ cho đơn đã hết hạn hoặc bị khóa. Vui lòng xử lý đơn hàng trước.");
        require(
                batch.getReservedQuantity() >= quantity && batch.getQuantityOnHand() >= quantity,
                "Tồn kho của lô không khớp với số lượng đã phân bổ.");

        batch.setQuantityOnHand(batch.getQuantityOnHand() - quantity);
        batch.setReservedQuantity(batch.getReservedQuantity() - quantity);
        batch.setUpdatedAt(now());
        inventoryBatchRepository.save(batch);
        allocation.setStatus(AllocationStatus.SOLD);
        stockAllocationRepository.save(allocation);

        Long orderItemId = allocation.getOrderItemId();
        inventoryJournalService.stock(
                batch.getVariantId(),
                batch.getBatchId(),
                StockTransactionType.SALE,
                -quantity,
                actorId,
                "Xuất bán số lượng đã giữ cho đơn.",
                "ORDER_ITEM",
                orderItemId);
        inventoryJournalService.audit(
                actorId,
                "SALE",
                "BATCH",
                batch.getBatchId(),
                "Xuất bán " + quantity + " cho dòng sản phẩm trong đơn #" + orderItemId + ".");
    }

    private InventoryBatch getBatchForUpdate(Long batchId) {
        InventoryBatch batch = inventoryBatchRepository.lockById(batchId).orElseThrow();
        return inventoryLockHelper.refresh(batch);
    }

    private InventoryOrderLine getOwnedOrderLineForUpdate(
            Authentication authentication, Long orderItemId) {
        Long shopId = shopService.getManagedShopId(authentication);
        InventoryOrderLine snapshot =
                inventoryOrderLineRepository
                        .findById(orderItemId)
                        .orElseThrow(
                                () ->
                                        new AccessDeniedException(
                                                "Bạn không có quyền truy cập sản phẩm trong đơn này."));
        // Keep lock ordering: order, order line, product, variant, then inventory batches.
        ShopOrder order =
                shopOrderRepository
                        .findForUpdateByShopOrderIdAndShopId(snapshot.getShopOrderId(), shopId)
                        .orElseThrow(
                                () -> new AccessDeniedException("Sản phẩm trong đơn không thuộc cửa hàng của bạn."));
        inventoryLockHelper.refresh(order);
        InventoryOrderLine line = inventoryOrderLineRepository.lockById(orderItemId).orElseThrow();
        inventoryLockHelper.refresh(line);
        require(
                line.getShopOrderId().equals(order.getShopOrderId()),
                "Thông tin sản phẩm trong đơn đã thay đổi. Vui lòng tải lại và thử lại.");
        ProductVariant variant =
                productVariantRepository.findById(line.getVariantId()).orElseThrow();
        Product product =
                productRepository
                        .lockOwned(variant.getProductId(), shopId)
                        .orElseThrow(
                                () ->
                                        new AccessDeniedException(
                                                "Phân loại trong đơn không thuộc cửa hàng của bạn."));
        inventoryLockHelper.refresh(product);
        ProductVariant lockedVariant =
                productVariantRepository.lockById(line.getVariantId()).orElseThrow();
        inventoryLockHelper.refresh(lockedVariant);
        return line;
    }
}
