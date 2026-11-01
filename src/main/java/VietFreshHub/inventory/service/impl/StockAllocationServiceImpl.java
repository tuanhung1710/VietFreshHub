package VietFreshHub.inventory.service.impl;

import static VietFreshHub.inventory.rule.InventoryRules.available;
import static VietFreshHub.inventory.rule.InventoryRules.expired;
import static VietFreshHub.inventory.rule.InventoryRules.now;
import static VietFreshHub.inventory.rule.InventoryRules.require;
import static VietFreshHub.inventory.rule.InventoryRules.today;
import VietFreshHub.auth.service.AuthService;
import VietFreshHub.inventory.entity.BatchMarkdown;
import VietFreshHub.inventory.entity.InventoryBatch;
import VietFreshHub.inventory.entity.InventoryOrderLine;
import VietFreshHub.inventory.entity.StockAllocation;
import VietFreshHub.inventory.enums.AllocationStatus;
import VietFreshHub.inventory.enums.BatchStatus;
import VietFreshHub.inventory.enums.StockTransactionType;
import VietFreshHub.inventory.repository.InventoryBatchRepository;
import VietFreshHub.inventory.repository.InventoryOrderLineRepository;
import VietFreshHub.inventory.repository.StockAllocationRepository;
import VietFreshHub.inventory.rule.InventoryRules;
import VietFreshHub.inventory.service.BatchMarkdownLookup;
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
    private final BatchMarkdownLookup batchMarkdownLookup;
    private final ShopService shopService;
    private final AuthService authService;
    private final InventoryJournalService inventoryJournalService;
    private final InventoryLockHelper inventoryLockHelper;
    private final ProductAvailabilityService productAvailabilityService;

    /**
     * Giữ đủ hàng cho dòng đơn đã được người bán xác nhận, ưu tiên lô hết hạn sớm.
     * Kiểm tra lại giá hiện tại và tái sử dụng phân bổ hợp lệ để tránh giữ hàng hai lần.
     */
    @Override
    public List<Long> reserve(Authentication authentication, Long orderItemId) {
        // Khóa dòng đơn và dữ liệu liên quan trước khi kiểm tra trạng thái, giá và tồn kho.
        InventoryOrderLine line = getOwnedOrderLineForUpdate(authentication, orderItemId);
        ShopOrder order = shopOrderRepository.findById(line.getShopOrderId()).orElseThrow();
        require(
                List.of("CONFIRMED", "PREPARING").contains(order.getStatus()),
                "Chỉ được giữ hàng sau khi người bán xác nhận đơn hàng.");

        List<StockAllocation> existing =
                stockAllocationRepository.findByOrderItemIdOrderByAllocationId(orderItemId);
        if (!existing.isEmpty()) {
            // Yêu cầu lặp lại chỉ trả phân bổ đang giữ đủ hàng, tránh tăng lượng giữ lần thứ hai.
            return existingReservationIds(existing, line.getQuantity());
        }

        ProductVariant variant =
                productVariantRepository.findById(line.getVariantId()).orElseThrow();
        validateSellableVariant(variant);
        LocalDate date = today();
        List<InventoryBatch> candidates = lockBatchesAtOrderPrice(line, variant, date);
        require(
                line.getQuantity() != null && line.getQuantity() > 0,
                "Số lượng sản phẩm trong đơn phải lớn hơn 0.");
        require(
                totalAvailable(candidates, date) >= line.getQuantity(),
                "Không đủ số lượng khả dụng ở mức giá đặt hàng.");

        // Chỉ bắt đầu ghi phân bổ sau khi đã chắc chắn toàn bộ số lượng có thể được đáp ứng.
        Long actorId = authService.getCurrentUserId(authentication);
        return allocateStock(line, candidates, actorId, date);
    }

    /** Khóa lô, kiểm tra giá đặt hàng rồi chỉ giữ các lô áp dụng đúng mức giá đó. */
    private List<InventoryBatch> lockBatchesAtOrderPrice(
            InventoryOrderLine line, ProductVariant variant, LocalDate date) {
        // Khóa lô trước khi đọc tồn, rồi tính lại giá hợp lệ từ chính các lô có thể cấp hàng.
        List<InventoryBatch> candidates = lockAvailableBatches(variant.getVariantId(), date);
        List<BatchMarkdown> markdowns = batchMarkdownLookup.findActive(candidates);
        BigDecimal minimumPrice =
                lowestAvailablePrice(candidates, markdowns, variant.getPrice(), date);
        require(
                line.getUnitPrice().compareTo(minimumPrice) == 0,
                "Giá sản phẩm đã thay đổi. Vui lòng tải lại để cập nhật giá.");

        if (minimumPrice.compareTo(variant.getPrice()) < 0) {
            // Đơn mua theo giá giảm chỉ được nhận hàng từ lô có đúng mức giảm đó.
            candidates =
                    batchesAtMarkdownPrice(
                            candidates, markdowns, variant.getPrice(), minimumPrice, date);
        }

        return candidates;
    }

    /**
     * Trả lại ID phân bổ cũ khi toàn bộ vẫn đang giữ và tổng lượng đúng với dòng đơn.
     * Từ chối tự động giữ lại nếu phân bổ đã xuất bán, đã giải phóng hoặc số lượng đã thay đổi.
     */
    private List<Long> existingReservationIds(
            List<StockAllocation> allocations, int expectedQuantity) {
        long reservedQuantity = 0;
        List<Long> allocationIds = new ArrayList<>();
        boolean allReserved = true;
        // Một dòng đơn có thể chia ra nhiều lô; phải kiểm tra trạng thái và tổng lượng của tất cả.
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

    /**
     * Kiểm tra điều kiện kinh doanh của phân loại sau khi đã khóa dòng đơn, sản phẩm và phân loại.
     */
    private void validateSellableVariant(ProductVariant variant) {
        // getOwnedOrderLineForUpdate đã khóa sản phẩm và phân loại trước khi đến bước này.
        Product product = productRepository.findById(variant.getProductId()).orElseThrow();
        boolean sellable = productAvailabilityService.canSellVariant(product.getShopId(), variant);
        require(sellable, "Sản phẩm hoặc phân loại hiện không được mở bán.");
    }

    /**
     * Khóa lô theo ID để tránh đảo thứ tự khóa, rồi chọn lô còn lượng khả dụng.
     * Sắp lại theo hạn sớm nhất để cấp hàng; lô không có hạn sử dụng được xếp cuối.
     */
    private List<InventoryBatch> lockAvailableBatches(Long variantId, LocalDate date) {
        List<InventoryBatch> candidates = new ArrayList<>();
        List<InventoryBatch> snapshots =
                inventoryBatchRepository.findByVariantIdOrderByExpiryDateAscBatchIdAsc(variantId);
        // Thứ tự khóa theo ID để các giao dịch không chờ khóa chéo nhau.
        List<InventoryBatch> snapshotsById = snapshots.stream()
                .sorted(Comparator.comparing(InventoryBatch::getBatchId))
                .toList();
        for (InventoryBatch snapshot : snapshotsById) {
            InventoryBatch batch = getBatchForUpdate(snapshot.getBatchId());
            if (available(batch, date) <= 0) {
                continue;
            }
            candidates.add(batch);
        }
        // Lô cũ chưa có hạn dùng được xếp cuối; lô cùng hạn được ưu tiên theo ID tăng dần.
        Comparator<LocalDate> expiryOrder = Comparator.nullsLast(Comparator.naturalOrder());
        Comparator<InventoryBatch> fefoOrder =
                Comparator.comparing(InventoryBatch::getExpiryDate, expiryOrder);
        candidates.sort(fefoOrder.thenComparing(InventoryBatch::getBatchId));
        return candidates;
    }

    /** Tìm mức giá thấp nhất giữa giá gốc và các giá giảm còn áp dụng được trên lô. */
    private BigDecimal lowestAvailablePrice(
            List<InventoryBatch> batches,
            List<BatchMarkdown> markdowns,
            BigDecimal basePrice,
            LocalDate date) {
        // Bắt đầu từ giá gốc, chỉ hạ giá khi có đợt giảm áp dụng được cho lô còn khả dụng.
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

    /** Chỉ giữ các lô có giảm giá hợp lệ đúng mức giá đặt hàng để tránh cấp hàng sai giá. */
    private List<InventoryBatch> batchesAtMarkdownPrice(
            List<InventoryBatch> batches,
            List<BatchMarkdown> markdowns,
            BigDecimal basePrice,
            BigDecimal price,
            LocalDate date) {
        // Một lô chỉ cần xuất hiện một lần, dù có nhiều bản ghi giảm giá cùng mức giá.
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

    /** Cộng lượng có thể bán từ các lô theo trạng thái, hạn sử dụng và hàng đang giữ. */
    private long totalAvailable(List<InventoryBatch> batches, LocalDate date) {
        long total = 0;
        for (InventoryBatch batch : batches) {
            total += available(batch, date);
        }
        return total;
    }

    /** Lần lượt lấy lượng cần từ các lô đã sắp thứ tự cho đến khi giữ đủ hàng cho dòng đơn. */
    private List<Long> allocateStock(
            InventoryOrderLine line, List<InventoryBatch> batches, Long actorId, LocalDate date) {
        int neededQuantity = line.getQuantity();
        List<Long> allocationIds = new ArrayList<>();
        for (InventoryBatch batch : batches) {
            // Mỗi lô chỉ cấp tối đa phần đơn còn thiếu và không vượt lượng khả dụng của lô.
            int quantity = Math.min(neededQuantity, available(batch, date));
            if (quantity == 0) {
                continue;
            }
            StockAllocation allocation =
                    reserveBatch(line.getOrderItemId(), batch, quantity, actorId);
            allocationIds.add(allocation.getAllocationId());
            // Danh sách đã ưu tiên hạn sớm; dừng ngay khi các lô đã cấp đủ số lượng cần giữ.
            neededQuantity -= quantity;
            if (neededQuantity == 0) {
                break;
            }
        }
        return allocationIds;
    }

    /** Tăng lượng giữ của lô, tạo phân bổ cho dòng đơn và ghi nhật ký giữ hàng. */
    private StockAllocation reserveBatch(
            Long orderItemId, InventoryBatch batch, int quantity, Long actorId) {
        // Giữ hàng chỉ tăng reservedQuantity; hàng vẫn còn trong tồn thực tế của lô.
        batch.setReservedQuantity(batch.getReservedQuantity() + quantity);
        batch.setUpdatedAt(now());
        inventoryBatchRepository.save(batch);

        // Ghi rõ phần số lượng của dòng đơn được giữ từ lô nào để có thể xuất hoặc giải phóng sau.
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

    /**
     * Xuất bán phần đã giữ khi đơn sẵn sàng giao, đang giao hoặc hoàn tất.
     * Chỉ xử lý khi đã phân bổ đủ; nếu toàn bộ đã xuất bán thì không trừ kho lần nữa.
     */
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
        // Nếu lần gọi trước đã xuất toàn bộ thì kết thúc để không trừ kho hai lần.
        if (allAllocationsHaveStatus(allocations, AllocationStatus.SOLD)) {
            return;
        }
        require(
                allAllocationsHaveStatus(allocations, AllocationStatus.RESERVED),
                "Trạng thái phân bổ hàng không hợp lệ. Vui lòng kiểm tra lại đơn hàng.");
        // Chỉ xuất khi toàn bộ phân bổ còn đang giữ và tổng lượng khớp dòng đơn.
        for (StockAllocation allocation : allocations) {
            sellBatch(allocation, actorId);
        }
    }

    /** Yêu cầu phân bổ không rỗng, chưa có phần bị giải phóng và tổng lượng đúng bằng dòng đơn. */
    private void validateCompleteAllocation(
            List<StockAllocation> allocations, int expectedQuantity) {
        long quantity = 0;
        boolean hasReleased = false;
        // Phân bổ đã giải phóng không còn bảo đảm có hàng, dù tổng lượng trong lịch sử vẫn đủ.
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

    /** Kiểm tra tất cả phân bổ có cùng trạng thái yêu cầu; danh sách rỗng cũng trả về true. */
    private boolean allAllocationsHaveStatus(
            List<StockAllocation> allocations, AllocationStatus status) {
        for (StockAllocation allocation : allocations) {
            if (allocation.getStatus() != status) {
                return false;
            }
        }
        return true;
    }

    /**
     * Trừ cả tồn thực tế và lượng giữ của lô còn hạn, đang hoạt động; đánh dấu phân bổ đã bán và ghi nhật ký.
     */
    private void sellBatch(StockAllocation allocation, Long actorId) {
        InventoryBatch batch = getBatchForUpdate(allocation.getBatchId());
        int quantity = allocation.getQuantity();
        // Kiểm tra lại lô tại thời điểm xuất: lô có thể đã hết hạn hoặc bị khóa sau lúc giữ hàng.
        require(
                !expired(batch, today()) && batch.getStatus() == BatchStatus.ACTIVE,
                "Lô hàng đã giữ cho đơn đã hết hạn hoặc bị khóa. Vui lòng xử lý đơn hàng trước.");
        require(
                batch.getReservedQuantity() >= quantity && batch.getQuantityOnHand() >= quantity,
                "Tồn kho của lô không khớp với số lượng đã phân bổ.");

        // Hàng thực sự rời kho nên giảm cả tồn thực tế và phần đang giữ, rồi đánh dấu đã bán.
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

    /** Khóa ghi và tải lại lô để dùng dữ liệu tồn kho mới nhất trong giao dịch. */
    private InventoryBatch getBatchForUpdate(Long batchId) {
        InventoryBatch batch = inventoryBatchRepository.lockById(batchId).orElseThrow();
        return inventoryLockHelper.refresh(batch);
    }

    /**
     * Lấy dòng đơn thuộc cửa hàng và khóa các dữ liệu liên quan trước khi xử lý kho.
     * Thứ tự khóa: đơn của shop, dòng đơn, sản phẩm, phân loại; lô được khóa sau đó.
     */
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
        // Thứ tự khóa thống nhất: đơn của shop, dòng đơn, sản phẩm, phân loại rồi các lô.
        ShopOrder order =
                shopOrderRepository
                        .findForUpdateByShopOrderIdAndShopId(snapshot.getShopOrderId(), shopId)
                        .orElseThrow(
                                () -> new AccessDeniedException("Sản phẩm trong đơn không thuộc cửa hàng của bạn."));
        inventoryLockHelper.refresh(order);
        InventoryOrderLine line = inventoryOrderLineRepository.lockById(orderItemId).orElseThrow();
        inventoryLockHelper.refresh(line);
        // Sau khi chờ khóa, xác nhận dòng đơn vẫn thuộc đơn của shop vừa kiểm tra quyền.
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
        // Sản phẩm luôn được khóa trước SKU; các hàm kho khác cũng dùng cùng thứ tự này.
        ProductVariant lockedVariant =
                productVariantRepository.lockById(line.getVariantId()).orElseThrow();
        inventoryLockHelper.refresh(lockedVariant);
        return line;
    }
}
