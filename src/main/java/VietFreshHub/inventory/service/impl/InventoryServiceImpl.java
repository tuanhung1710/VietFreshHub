package VietFreshHub.inventory.service.impl;

import static VietFreshHub.inventory.rule.InventoryRules.available;
import static VietFreshHub.inventory.rule.InventoryRules.expired;
import static VietFreshHub.inventory.rule.InventoryRules.nearExpiry;
import static VietFreshHub.inventory.rule.InventoryRules.now;
import static VietFreshHub.inventory.rule.InventoryRules.reason;
import static VietFreshHub.inventory.rule.InventoryRules.require;
import static VietFreshHub.inventory.rule.InventoryRules.today;
import VietFreshHub.auth.service.AuthService;
import VietFreshHub.inventory.dto.ActivityHistoryFilter;
import VietFreshHub.inventory.dto.AllocationResponse;
import VietFreshHub.inventory.dto.AuditResponse;
import VietFreshHub.inventory.dto.BatchRequest;
import VietFreshHub.inventory.dto.BatchResponse;
import VietFreshHub.inventory.dto.DisposalResult;
import VietFreshHub.inventory.dto.MarkdownRequest;
import VietFreshHub.inventory.dto.StockChangeRequest;
import VietFreshHub.inventory.entity.BatchMarkdown;
import VietFreshHub.inventory.entity.InventoryBatch;
import VietFreshHub.inventory.entity.InventoryOrderLine;
import VietFreshHub.inventory.enums.BatchStatus;
import VietFreshHub.inventory.enums.MarkdownStatus;
import VietFreshHub.inventory.enums.StockTransactionType;
import VietFreshHub.inventory.repository.BatchMarkdownRepository;
import VietFreshHub.inventory.repository.InventoryBatchRepository;
import VietFreshHub.inventory.repository.InventoryOrderLineRepository;
import VietFreshHub.inventory.repository.StockAllocationRepository;
import VietFreshHub.inventory.rule.InventoryRules;
import VietFreshHub.inventory.service.BatchAllocationHistoryService;
import VietFreshHub.inventory.service.InventoryActivityHistoryService;
import VietFreshHub.inventory.service.InventoryJournalService;
import VietFreshHub.inventory.service.InventoryService;
import VietFreshHub.inventory.util.InventoryLockHelper;
import VietFreshHub.product.entity.Product;
import VietFreshHub.product.entity.ProductVariant;
import VietFreshHub.product.enums.ProductStatus;
import VietFreshHub.product.enums.VariantStatus;
import VietFreshHub.product.repository.ProductRepository;
import VietFreshHub.product.repository.ProductVariantRepository;
import VietFreshHub.product.service.ProductAvailabilityService;
import VietFreshHub.shop.service.ShopService;
import VietFreshHub.order.entity.ShopOrder;
import VietFreshHub.order.repository.ShopOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryServiceImpl implements InventoryService {

    private final InventoryActivityHistoryService activityHistoryService;
    private final ProductAvailabilityService productAvailabilityService;

    private final InventoryBatchRepository inventoryBatchRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductRepository productRepository;
    private final BatchMarkdownRepository batchMarkdownRepository;
    private final StockAllocationRepository stockAllocationRepository;
    private final ShopService shopService;
    private final AuthService authService;
    private final InventoryJournalService inventoryJournalService;
    private final InventoryLockHelper inventoryLockHelper;
    private final BatchAllocationHistoryService batchAllocationHistoryService;
    private final InventoryOrderLineRepository inventoryOrderLineRepository;
    private final ShopOrderRepository shopOrderRepository;

    /**
     * Nhập lô mới cho phân loại thuộc cửa hàng và ghi lịch sử kho.
     * Nếu phân loại đang hết hàng, chuyển lại sang trạng thái hoạt động sau khi nhập.
     */
    @Override
    @Transactional
    public void restock(Authentication authentication, Long variantId, BatchRequest form) {

        ProductVariant variant = getOwnedVariantForUpdate(authentication, variantId);
        Product product = productRepository.findById(variant.getProductId()).orElseThrow();
        require(
                product.getStatus() != ProductStatus.DELETED
                        && variant.getStatus() != VariantStatus.DELETED,
                "Không thể nhập kho cho sản phẩm hoặc phân loại đã ngừng kinh doanh.");

        InventoryRules.dates(form.getReceivedDate(), form.getExpiryDate(), today());
        require(form.getQuantity() != null && form.getQuantity() > 0, "Số lượng nhập phải lớn hơn 0.");
        String changeReason = reason(form.getReason());
        validateCost(form);

        InventoryBatch batch = createBatch(variantId, form);

        if (variant.getStatus() == VariantStatus.OUT_OF_STOCK) {
            variant.setStatus(VariantStatus.ACTIVE);
            variant.setUpdatedAt(now());
            productVariantRepository.save(variant);
        }

        // Ghi số lượng tăng trong nhật ký kho và lý do thao tác trong lịch sử hoạt động.
        Long actorId = authService.getCurrentUserId(authentication);
        inventoryJournalService.stock(
                variantId,
                batch.getBatchId(),
                StockTransactionType.RESTOCK,
                form.getQuantity(),
                actorId,
                changeReason,
                "BATCH",
                batch.getBatchId());
        inventoryJournalService.audit(
                actorId,
                "RESTOCK",
                "BATCH",
                batch.getBatchId(),
                changeReason + "; 0 → " + form.getQuantity());
    }

    /** Lưu lô nhập với lượng giữ bằng 0 và chuyển ngày nhập tại Việt Nam sang thời điểm UTC. */
    private InventoryBatch createBatch(Long variantId, BatchRequest form) {
        InventoryBatch batch = new InventoryBatch();
        batch.setVariantId(variantId);
        batch.setQuantityOnHand(form.getQuantity());
        batch.setReservedQuantity(0);
        batch.setStatus(BatchStatus.ACTIVE);
        batch.setCostPrice(form.getCostPrice());
        batch.setExpiryDate(form.getExpiryDate());
        batch.setReceivedAt(InventoryRules.startOfDayUtc(form.getReceivedDate()));
        batch.setCreatedAt(now());
        batch.setUpdatedAt(now());
        return inventoryBatchRepository.saveAndFlush(batch);
    }

    /**
     * Tạo biểu mẫu sửa lô từ dữ liệu đã kiểm tra quyền, đổi thời điểm nhập về ngày tại Việt Nam.
     */
    @Override
    public BatchRequest batchForm(Authentication authentication, Long batchId) {
        InventoryBatch batch = getOwnedBatchForRead(authentication, batchId);
        BatchRequest form = new BatchRequest();
        form.setQuantity(1);
        form.setCostPrice(batch.getCostPrice());
        form.setExpiryDate(batch.getExpiryDate());
        form.setReceivedDate(InventoryRules.shopDate(batch.getReceivedAt()));
        return form;
    }

    /**
     * Cập nhật ngày nhập, hạn sử dụng và giá vốn của lô, đồng thời ghi lại thông tin trước và sau sửa.
     */
    @Override
    @Transactional
    public void updateBatch(Authentication authentication, Long batchId, BatchRequest form) {

        InventoryBatch batch = getOwnedBatchForUpdate(authentication, batchId);
        validateCost(form);
        String changeReason = reason(form.getReason());
        validateBatchDates(batch, form);

        // Chụp mô tả cũ trước khi gán dữ liệu mới để lịch sử thể hiện được thay đổi.
        String before = describeBatchDatesAndCost(batch);
        batch.setExpiryDate(form.getExpiryDate());
        batch.setCostPrice(form.getCostPrice());
        batch.setReceivedAt(InventoryRules.startOfDayUtc(form.getReceivedDate()));
        batch.setUpdatedAt(now());
        inventoryBatchRepository.save(batch);

        inventoryJournalService.audit(
                authService.getCurrentUserId(authentication),
                "EDIT",
                "BATCH",
                batchId,
                changeReason + "; " + before + " → " + describeBatchDatesAndCost(batch));
    }

    /**
     * Kiểm tra ngày nhập và hạn sử dụng khi sửa lô.
     * Nếu đổi hạn, lô cũ phải chưa hết hạn và hạn mới phải hợp lệ tại ngày hiện tại.
     */
    private void validateBatchDates(InventoryBatch batch, BatchRequest form) {
        LocalDate date = today();
        require(
                form.getReceivedDate() != null && !form.getReceivedDate().isAfter(date),
                "Ngày nhập kho không được sau hôm nay.");
        if (!Objects.equals(form.getExpiryDate(), batch.getExpiryDate())) {

            require(!expired(batch, date), "Không thể đổi hạn sử dụng của lô đã hết hạn.");
            InventoryRules.dates(form.getReceivedDate(), form.getExpiryDate(), date);
        }
        require(
                form.getExpiryDate() != null
                        && !form.getExpiryDate().isBefore(form.getReceivedDate()),
                "Hạn sử dụng không được trước ngày nhập kho.");
    }

    /** Tạo mô tả hạn sử dụng và giá vốn để ghi lịch sử trước, sau khi chỉnh sửa lô. */
    private String describeBatchDatesAndCost(InventoryBatch batch) {
        return "Hạn sử dụng: " + batch.getExpiryDate() + " / Giá vốn: " + batch.getCostPrice();
    }

    /** Cho phép bỏ trống giá vốn nhưng từ chối giá trị âm. */
    private void validateCost(BatchRequest form) {
        require(
                form.getCostPrice() == null || form.getCostPrice().signum() >= 0,
                "Giá vốn không được âm.");
    }

    /**
     * Điều chỉnh tồn thực tế của lô sau khi kiểm tra quyền và số lượng giữ cho đơn.
     * Ghi cả biến động kho và lịch sử thao tác với lý do điều chỉnh.
     */
    @Override
    @Transactional
    public void adjust(Authentication authentication, Long batchId, StockChangeRequest form) {
        InventoryBatch batch = getOwnedBatchForUpdate(authentication, batchId);
        String changeReason = reason(form.getReason());
        require(form.getQuantity() != null, "Vui lòng nhập số lượng điều chỉnh.");
        int previousQuantity = batch.getQuantityOnHand();

        int nextQuantity = InventoryRules.adjustedQuantity(batch, form.getQuantity(), today());
        updateQuantity(batch, nextQuantity);

        Long actorId = authService.getCurrentUserId(authentication);
        inventoryJournalService.stock(
                batch.getVariantId(),
                batchId,
                StockTransactionType.ADJUSTMENT,
                form.getQuantity(),
                actorId,
                changeReason,
                "BATCH",
                batchId);
        inventoryJournalService.audit(
                actorId,
                "ADJUSTMENT",
                "BATCH",
                batchId,
                changeReason + "; " + previousQuantity + " → " + nextQuantity);
    }

    /**
     * Hủy toàn bộ phần chưa giữ cho đơn của một lô hết hạn.
     * Trả về số lô đã hủy và số lô vẫn còn hàng giữ để người bán tiếp tục xử lý đơn.
     */
    @Override
    @Transactional
    public DisposalResult dispose(Authentication authentication, Long batchId) {
        InventoryBatch batch = getOwnedBatchForUpdate(authentication, batchId);
        LocalDate date = today();
        require(expired(batch, date), "Chỉ sử dụng thao tác này cho lô đã hết hạn.");
        validateDisposalBalance(batch);

        int quantity = InventoryRules.disposableQuantity(batch, date);
        if (quantity > 0) {
            disposeBatch(batch, quantity, authService.getCurrentUserId(authentication), date);
        }
        return new DisposalResult(quantity > 0 ? 1 : 0, batch.getReservedQuantity() > 0 ? 1 : 0);
    }

    @Override
    @Transactional
    public DisposalResult disposeExpiredProduct(Authentication authentication, Long productId) {
        Long shopId = shopService.getManagedShopId(authentication);
        Product product = productRepository.lockOwned(productId, shopId)
                .orElseThrow(() -> new AccessDeniedException("Sản phẩm không thuộc cửa hàng của bạn."));
        inventoryLockHelper.refresh(product);
        List<ProductVariant> variants = productVariantRepository.findByProductIdOrderByVariantId(productId);

        for (ProductVariant snapshot : variants) {
            inventoryLockHelper.refresh(productVariantRepository.lockById(snapshot.getVariantId()).orElseThrow());
        }
        if (variants.isEmpty()) {
            return new DisposalResult(0, 0);
        }
        List<InventoryBatch> snapshots = inventoryBatchRepository.findByVariantIdInOrderByExpiryDateAscBatchIdAsc(
                variants.stream().map(ProductVariant::getVariantId).toList());
        LocalDate date = today();
        Long actorId = authService.getCurrentUserId(authentication);
        int disposedBatches = 0;
        int reservedBatches = 0;

        for (InventoryBatch snapshot : snapshots.stream().sorted(Comparator.comparing(InventoryBatch::getBatchId)).toList()) {
            InventoryBatch batch = inventoryLockHelper.refresh(
                    inventoryBatchRepository.lockById(snapshot.getBatchId()).orElseThrow());
            if (!expired(batch, date)) {
                continue;
            }
            validateDisposalBalance(batch);

            if (batch.getReservedQuantity() > 0) {
                reservedBatches++;
            }
            int quantity = InventoryRules.disposableQuantity(batch, date);
            if (quantity > 0) {
                disposeBatch(batch, quantity, actorId, date);
                disposedBatches++;
            }
        }
        return new DisposalResult(disposedBatches, reservedBatches);
    }

    private void validateDisposalBalance(InventoryBatch batch) {
        require(batch.getQuantityOnHand() != null && batch.getReservedQuantity() != null
                        && batch.getReservedQuantity() >= 0 && batch.getQuantityOnHand() >= batch.getReservedQuantity(),
                "Dữ liệu tồn kho của lô không hợp lệ. Vui lòng kiểm tra trước khi hủy hàng.");
    }

    private void disposeBatch(InventoryBatch batch, int quantity, Long actorId, LocalDate date) {
        String changeReason = InventoryRules.EXPIRED_DISPOSAL_REASON;
        Long batchId = batch.getBatchId();
        int previousQuantity = batch.getQuantityOnHand();
        int nextQuantity = InventoryRules.disposedQuantity(batch, quantity, date);
        updateQuantity(batch, nextQuantity);

        inventoryJournalService.stock(
                batch.getVariantId(),
                batchId,
                StockTransactionType.EXPIRED,
                -quantity,
                actorId,
                changeReason,
                "BATCH",
                batchId);
        inventoryJournalService.audit(
                actorId,
                "DISPOSE",
                "BATCH",
                batchId,
                changeReason
                        + "; "
                        + previousQuantity
                        + " → "
                        + nextQuantity
                        + "; đã giữ cho đơn: "
                        + batch.getReservedQuantity());
    }

    /** Lưu số lượng tồn thực tế mới của lô và cập nhật thời điểm thay đổi. */
    private void updateQuantity(InventoryBatch batch, int quantity) {
        batch.setQuantityOnHand(quantity);
        batch.setUpdatedAt(now());
        inventoryBatchRepository.save(batch);
    }

    /** Khóa hoặc mở khóa lô và ghi lý do, giữ nguyên lượng hàng đã giữ cho đơn. */
    @Override
    @Transactional
    public void block(Authentication authentication, Long batchId, boolean blocked, String value) {

        InventoryBatch batch = getOwnedBatchForUpdate(authentication, batchId);
        String changeReason = reason(value);
        BatchStatus status = BatchStatus.ACTIVE;
        String action = "UNBLOCK";
        if (blocked) {
            status = BatchStatus.BLOCKED;
            action = "BLOCK";
        }
        batch.setStatus(status);
        batch.setUpdatedAt(now());
        inventoryBatchRepository.save(batch);
        inventoryJournalService.audit(
                authService.getCurrentUserId(authentication),
                action,
                "BATCH",
                batchId,
                changeReason + "; giữ nguyên số lượng đã giữ cho đơn: " + batch.getReservedQuantity());
    }

    /**
     * Thiết lập giá giảm cho lô đủ điều kiện và hủy các đợt giảm giá đang bật trước đó.
     * Kiểm tra giá, hạn áp dụng và quyền quản lý lô trước khi lưu.
     */
    @Override
    @Transactional
    public void markdown(Authentication authentication, Long batchId, MarkdownRequest form) {

        InventoryBatch batch = getOwnedBatchForUpdate(authentication, batchId);
        ProductVariant variant =
                productVariantRepository.findById(batch.getVariantId()).orElseThrow();
        Product product = productRepository.findById(variant.getProductId()).orElseThrow();
        require(markdownAllowed(product, variant), "Chỉ áp dụng giảm giá cho phân loại đang bán, có giá hợp lệ, không bị Admin khóa và shop đang nhận đơn.");

        LocalDate date = today();
        InventoryRules.markdown(
                batch, variant.getPrice(), form.getPrice(), form.getEndDate(), date);
        String changeReason = reason(form.getReason());
        Long actorId = authService.getCurrentUserId(authentication);

        cancelActiveMarkdowns(batchId);
        BatchMarkdown markdown = new BatchMarkdown();
        markdown.setBatchId(batchId);
        markdown.setPrice(form.getPrice());
        markdown.setStartDate(date);
        markdown.setEndDate(form.getEndDate());
        markdown.setStatus(MarkdownStatus.ACTIVE);
        markdown.setNote(changeReason);
        markdown.setCreatedBy(actorId);
        markdown.setCreatedAt(now());
        batchMarkdownRepository.save(markdown);
        inventoryJournalService.audit(
                actorId,
                "MARKDOWN",
                "BATCH",
                batchId,
                changeReason
                        + "; giá bán phân loại: "
                        + variant.getPrice()
                        + "; giá giảm của lô: "
                        + form.getPrice()
                        + "; kết thúc: "
                        + form.getEndDate());
    }

    /** Dừng giảm giá của lô thuộc cửa hàng và ghi lý do vào lịch sử thao tác. */
    @Override
    @Transactional
    public void cancelMarkdown(Authentication authentication, Long batchId, String value) {
        getOwnedBatchForUpdate(authentication, batchId);
        String changeReason = reason(value);
        cancelActiveMarkdowns(batchId);
        inventoryJournalService.audit(
                authService.getCurrentUserId(authentication),
                "CANCEL_MARKDOWN",
                "BATCH",
                batchId,
                changeReason);
    }

    /** Chuyển tất cả đợt giảm giá đang bật của lô sang trạng thái đã hủy. */
    private void cancelActiveMarkdowns(Long batchId) {
        List<BatchMarkdown> markdowns =
                batchMarkdownRepository.findByBatchIdAndStatus(batchId, MarkdownStatus.ACTIVE);
        for (BatchMarkdown markdown : markdowns) {
            markdown.setStatus(MarkdownStatus.CANCELLED);
            batchMarkdownRepository.save(markdown);
        }
    }

    /** Đọc lịch sử hoạt động theo bộ lọc, gồm nhãn đối tượng và người thao tác. */
    @Override
    public Page<AuditResponse> activityHistory(Authentication authentication,
            ActivityHistoryFilter filters, int page, int size) {
        return activityHistoryService.search(authentication, filters, page, size);
    }

    /** Lấy chi tiết lô thuộc cửa hàng, gồm tồn kho, hạn, giảm giá, phân bổ và mã đơn liên quan. */
    @Override
    public BatchResponse batch(Authentication authentication, Long batchId) {
        InventoryBatch batch = getOwnedBatchForRead(authentication, batchId);
        LocalDate date = today();
        BatchMarkdown markdown = findCurrentMarkdown(batchId, date);
        ProductVariant variant =
                productVariantRepository.findById(batch.getVariantId()).orElseThrow();
        Product product = productRepository.findById(variant.getProductId()).orElseThrow();
        BatchResponse response = BatchResponse.builder()
                .batch(batch)
                .available(available(batch, date))
                .expired(expired(batch, date))
                .nearExpiry(nearExpiry(batch, date, product.getExpiryWarningDays()))
                .markdown(markdown)
                .allocations(stockAllocationRepository.findByBatchIdOrderByAllocationIdDesc(batchId))
                .productName(product.getName())
                .sku(variant.getSku())
                .unit(variant.getUnit())
                .markdownEligible(InventoryRules.markdownEligible(batch, date))
                .expiryWarningDays(product.getExpiryWarningDays())
                .daysUntilExpiry(InventoryRules.daysUntilExpiry(batch, date))
                .markdownAllowed(markdownAllowed(product, variant))
                .build();
        response.setVariantName(variant.getVariantName());
        List<AllocationResponse> details = batchAllocationHistoryService.load(
                batchId, variant.getVariantId(), product.getShopId(), variant.getUnit(), response.getAllocations());
        List<Long> lineIds = details.stream().map(AllocationResponse::getOrderItemId).toList();
        Map<Long, String> orderCodes = orderCodesForLines(lineIds, product.getShopId());
        List<AllocationResponse> allocationDetails = new ArrayList<>();
        for (AllocationResponse detail : details) {
            String orderCode = orderCodes.get(detail.getOrderItemId());
            if (orderCode != null) {
                allocationDetails.add(new AllocationResponse(
                        detail.getAllocationId(), detail.getOrderItemId(), orderCode,
                        detail.getQuantity(), detail.getUnit(), detail.getStatus(),
                        detail.getUpdatedAt(), detail.getHistory()));
            }
        }
        response.setAllocationDetails(allocationDetails);
        response.setAllocationOrderCodes(orderCodes);
        return response;
    }

    private Map<Long, String> orderCodesForLines(List<Long> lineIds, Long shopId) {
        Map<Long, String> orderCodes = new HashMap<>();
        if (lineIds.isEmpty()) return orderCodes;
        Map<Long, Long> shopOrderIds = new HashMap<>();
        for (InventoryOrderLine line : inventoryOrderLineRepository.findAllById(lineIds)) {
            shopOrderIds.put(line.getOrderItemId(), line.getShopOrderId());
        }
        Map<Long, String> references = new HashMap<>();
        for (ShopOrder order : shopOrderRepository.findAllById(shopOrderIds.values())) {
            if (shopId.equals(order.getShopId())) {
                references.put(order.getShopOrderId(), order.getShopOrderCode());
            }
        }
        for (Map.Entry<Long, Long> line : shopOrderIds.entrySet()) {
            String code = references.get(line.getValue());
            if (code != null) orderCodes.put(line.getKey(), code);
        }
        return orderCodes;
    }

    private boolean markdownAllowed(Product product, ProductVariant variant) {
        return productAvailabilityService.canSellVariant(product.getShopId(), variant);
    }

    /**
     * Tìm đợt giảm giá đang bật có khoảng ngày bao gồm ngày tra cứu.
     * Hàm này chỉ xét trạng thái và ngày của đợt giảm, không kiểm tra điều kiện của lô.
     */
    private BatchMarkdown findCurrentMarkdown(Long batchId, LocalDate date) {
        for (BatchMarkdown markdown :
                batchMarkdownRepository.findByBatchIdAndStatus(batchId, MarkdownStatus.ACTIVE)) {
            if (!markdown.getEndDate().isBefore(date) && !markdown.getStartDate().isAfter(date)) {
                return markdown;
            }
        }
        return null;
    }

    private ProductVariant findVariantSnapshot(Long variantId) {
        return productVariantRepository.findById(variantId)
                .orElseThrow(() -> new AccessDeniedException("Bạn không có quyền truy cập phân loại này."));
    }

    private ProductVariant getOwnedVariantForRead(Authentication authentication, Long variantId) {
        Long shopId = shopService.getManagedShopId(authentication);
        ProductVariant variant = findVariantSnapshot(variantId);
        Product product = productRepository.findById(variant.getProductId())
                .orElseThrow(() -> new AccessDeniedException("Phân loại không thuộc cửa hàng của bạn."));
        if (!product.getShopId().equals(shopId)) {
            throw new AccessDeniedException("Phân loại không thuộc cửa hàng của bạn.");
        }
        return variant;
    }

    private ProductVariant getOwnedVariantForUpdate(Authentication authentication, Long variantId) {
        Long shopId = shopService.getManagedShopId(authentication);
        ProductVariant snapshot = findVariantSnapshot(variantId);
        Product product = productRepository.lockOwned(snapshot.getProductId(), shopId)
                .orElseThrow(() -> new AccessDeniedException("Phân loại không thuộc cửa hàng của bạn."));
        inventoryLockHelper.refresh(product);
        ProductVariant variant = productVariantRepository.lockById(variantId).orElseThrow();
        return inventoryLockHelper.refresh(variant);
    }

    private InventoryBatch findBatchSnapshot(Long batchId) {
        return inventoryBatchRepository.findById(batchId)
                .orElseThrow(() -> new AccessDeniedException("Bạn không có quyền truy cập lô hàng này."));
    }

    private InventoryBatch getOwnedBatchForRead(Authentication authentication, Long batchId) {
        InventoryBatch batch = findBatchSnapshot(batchId);
        getOwnedVariantForRead(authentication, batch.getVariantId());
        return batch;
    }

    private InventoryBatch getOwnedBatchForUpdate(Authentication authentication, Long batchId) {
        InventoryBatch snapshot = findBatchSnapshot(batchId);
        getOwnedVariantForUpdate(authentication, snapshot.getVariantId());
        InventoryBatch batch = inventoryBatchRepository.lockById(batchId).orElseThrow();
        return inventoryLockHelper.refresh(batch);
    }

}
