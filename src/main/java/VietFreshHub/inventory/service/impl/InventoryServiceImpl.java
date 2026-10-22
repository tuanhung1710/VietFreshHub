package VietFreshHub.inventory.service.impl;

import static VietFreshHub.inventory.rule.InventoryRules.available;
import static VietFreshHub.inventory.rule.InventoryRules.expired;
import static VietFreshHub.inventory.rule.InventoryRules.nearExpiry;
import static VietFreshHub.inventory.rule.InventoryRules.now;
import static VietFreshHub.inventory.rule.InventoryRules.reason;
import static VietFreshHub.inventory.rule.InventoryRules.require;
import static VietFreshHub.inventory.rule.InventoryRules.today;

import VietFreshHub.auth.service.AuthService;
import VietFreshHub.common.BusinessCodes;
import VietFreshHub.common.PageSizes;
import VietFreshHub.auth.entity.User;
import VietFreshHub.auth.repository.UserRepository;
import VietFreshHub.inventory.dto.AuditResponse;
import VietFreshHub.inventory.dto.ActivityHistoryFilter;
import VietFreshHub.inventory.dto.BatchRequest;
import VietFreshHub.inventory.dto.BatchResponse;
import VietFreshHub.inventory.dto.JournalResponse;
import VietFreshHub.inventory.dto.DisposalResult;
import VietFreshHub.inventory.dto.MarkdownRequest;
import VietFreshHub.inventory.dto.StockChangeRequest;
import VietFreshHub.inventory.entity.AuditEvent;
import VietFreshHub.inventory.entity.BatchMarkdown;
import VietFreshHub.inventory.entity.InventoryBatch;
import VietFreshHub.inventory.entity.InventoryTransaction;
import VietFreshHub.inventory.entity.InventoryOrderLine;
import VietFreshHub.inventory.entity.StockAllocation;
import VietFreshHub.inventory.repository.InventoryOrderLineRepository;
import VietFreshHub.order.entity.ShopOrder;
import VietFreshHub.order.repository.ShopOrderRepository;
import java.util.ArrayList;
import VietFreshHub.inventory.enums.BatchStatus;
import VietFreshHub.inventory.enums.MarkdownStatus;
import VietFreshHub.inventory.enums.StockTransactionType;
import VietFreshHub.inventory.repository.AuditEventRepository;
import VietFreshHub.inventory.repository.BatchMarkdownRepository;
import VietFreshHub.inventory.repository.InventoryBatchRepository;
import VietFreshHub.inventory.repository.InventoryTransactionRepository;
import VietFreshHub.inventory.repository.StockAllocationRepository;
import VietFreshHub.inventory.rule.InventoryRules;
import VietFreshHub.inventory.service.InventoryJournalService;
import VietFreshHub.inventory.service.InventoryService;
import VietFreshHub.inventory.util.InventoryLockHelper;
import VietFreshHub.product.entity.Product;
import VietFreshHub.product.entity.ProductVariant;
import VietFreshHub.product.service.ProductAvailabilityService;
import VietFreshHub.product.enums.ProductStatus;
import VietFreshHub.product.enums.VariantStatus;
import VietFreshHub.product.exception.CatalogException;
import VietFreshHub.product.repository.ProductRepository;
import VietFreshHub.product.repository.ProductVariantRepository;
import VietFreshHub.shop.service.ShopService;
import VietFreshHub.shop.entity.ShopMember;
import VietFreshHub.shop.repository.ShopMemberRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryServiceImpl implements InventoryService {

    private final ProductAvailabilityService productAvailabilityService;

    private final InventoryBatchRepository inventoryBatchRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductRepository productRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final BatchMarkdownRepository batchMarkdownRepository;
    private final AuditEventRepository auditEventRepository;
    private final StockAllocationRepository stockAllocationRepository;
    private final ShopService shopService;
    private final AuthService authService;
    private final InventoryJournalService inventoryJournalService;
    private final InventoryLockHelper inventoryLockHelper;
    private final UserRepository userRepository;
    private final ShopMemberRepository shopMemberRepository;
    private final ShopOrderRepository shopOrderRepository;
    private final InventoryOrderLineRepository inventoryOrderLineRepository;

    @Override
    @Transactional
    public void restock(Authentication authentication, Long variantId, BatchRequest form) {
        ProductVariant variant = getOwnedVariant(authentication, variantId, true);
        Product product = productRepository.findById(variant.getProductId()).orElseThrow();
        require(
                product.getStatus() != ProductStatus.DELETED
                        && variant.getStatus() != VariantStatus.DELETED,
                "Không thể nhập kho cho sản phẩm hoặc phân loại đã ngừng kinh doanh.");
        form.setExpiryDate(InventoryRules.resolveExpiryDate(
                form.getReceivedDate(), form.getShelfLifeDays(), form.getExpiryDate()));
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

    @Override
    public BatchRequest batchForm(Authentication authentication, Long batchId) {
        InventoryBatch batch = getOwnedBatch(authentication, batchId, false);
        BatchRequest form = new BatchRequest();
        form.setQuantity(1);
        form.setCostPrice(batch.getCostPrice());
        form.setExpiryDate(batch.getExpiryDate());
        form.setReceivedDate(InventoryRules.shopDate(batch.getReceivedAt()));
        return form;
    }

    @Override
    @Transactional
    public void updateBatch(Authentication authentication, Long batchId, BatchRequest form) {
        InventoryBatch batch = getOwnedBatch(authentication, batchId, true);
        validateCost(form);
        String changeReason = reason(form.getReason());
        form.setExpiryDate(InventoryRules.resolveExpiryDate(
                form.getReceivedDate(), form.getShelfLifeDays(), form.getExpiryDate()));
        validateBatchDates(batch, form);

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

    private String describeBatchDatesAndCost(InventoryBatch batch) {
        return "Hạn sử dụng: " + batch.getExpiryDate() + " / Giá vốn: " + batch.getCostPrice();
    }

    private void validateCost(BatchRequest form) {
        require(
                form.getCostPrice() == null || form.getCostPrice().signum() >= 0,
                "Giá vốn không được âm.");
    }

    @Override
    @Transactional
    public void adjust(Authentication authentication, Long batchId, StockChangeRequest form) {
        InventoryBatch batch = getOwnedBatch(authentication, batchId, true);
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

    @Override
    @Transactional
    public DisposalResult dispose(Authentication authentication, Long batchId) {
        InventoryBatch batch = getOwnedBatch(authentication, batchId, true);
        LocalDate date = today();
        require(expired(batch, date), "Chỉ sử dụng thao tác này cho lô đã hết hạn.");
        validateDisposalBalance(batch);
        int quantity = InventoryRules.disposableQuantity(batch, date);
        if (quantity > 0) disposeBatch(batch, quantity, authService.getCurrentUserId(authentication), date);
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
        // Match order confirmation/restock: product, variants in ID order, then lots in ID order.
        for (ProductVariant snapshot : variants) {
            inventoryLockHelper.refresh(productVariantRepository.lockById(snapshot.getVariantId()).orElseThrow());
        }
        if (variants.isEmpty()) return new DisposalResult(0, 0);
        List<InventoryBatch> snapshots = inventoryBatchRepository.findByVariantIdInOrderByExpiryDateAscBatchIdAsc(
                variants.stream().map(ProductVariant::getVariantId).toList());
        LocalDate date = today();
        Long actorId = authService.getCurrentUserId(authentication);
        int disposedBatches = 0;
        int reservedBatches = 0;
        for (InventoryBatch snapshot : snapshots.stream().sorted(Comparator.comparing(InventoryBatch::getBatchId)).toList()) {
            InventoryBatch batch = inventoryLockHelper.refresh(
                    inventoryBatchRepository.lockById(snapshot.getBatchId()).orElseThrow());
            if (!expired(batch, date)) continue;
            validateDisposalBalance(batch);
            if (batch.getReservedQuantity() > 0) reservedBatches++;
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

    private void updateQuantity(InventoryBatch batch, int quantity) {
        batch.setQuantityOnHand(quantity);
        batch.setUpdatedAt(now());
        inventoryBatchRepository.save(batch);
    }

    @Override
    @Transactional
    public void block(Authentication authentication, Long batchId, boolean blocked, String value) {
        InventoryBatch batch = getOwnedBatch(authentication, batchId, true);
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

    @Override
    @Transactional
    public void markdown(Authentication authentication, Long batchId, MarkdownRequest form) {
        InventoryBatch batch = getOwnedBatch(authentication, batchId, true);
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

    @Override
    @Transactional
    public void cancelMarkdown(Authentication authentication, Long batchId, String value) {
        getOwnedBatch(authentication, batchId, true);
        String changeReason = reason(value);
        cancelActiveMarkdowns(batchId);
        inventoryJournalService.audit(
                authService.getCurrentUserId(authentication),
                "CANCEL_MARKDOWN",
                "BATCH",
                batchId,
                changeReason);
    }

    private void cancelActiveMarkdowns(Long batchId) {
        List<BatchMarkdown> markdowns =
                batchMarkdownRepository.findByBatchIdAndStatus(batchId, MarkdownStatus.ACTIVE);
        for (BatchMarkdown markdown : markdowns) {
            markdown.setStatus(MarkdownStatus.CANCELLED);
            batchMarkdownRepository.save(markdown);
        }
    }

    @Override
    public Page<JournalResponse> journal(
            Authentication authentication,
            Long variantId,
            String type,
            String query,
            LocalDate from,
            LocalDate to,
            int page,
            int size) {
        return journal(authentication, variantId, null, type, query, from, to, page, size);
    }

    @Override
    public Page<JournalResponse> journal(
            Authentication authentication,
            Long variantId,
            Long batchId,
            String type,
            String query,
            LocalDate from,
            LocalDate to,
            int page,
            int size) {
        Long shopId = shopService.getManagedShopId(authentication);
        if (variantId != null) {
            getOwnedVariant(authentication, variantId, false);
        }
        if (batchId != null) {
            InventoryBatch batch = getOwnedBatch(authentication, batchId, false);
            if (variantId != null && !variantId.equals(batch.getVariantId())) {
                throw new AccessDeniedException("Lô hàng không thuộc phân loại được chọn.");
            }
        }
        StockTransactionType transactionType = parseStockType(type);
        require(from == null || to == null || !from.isAfter(to), "Ngày bắt đầu không được sau ngày kết thúc.");
        LocalDateTime start = LocalDateTime.of(1900, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(9998, 1, 1, 0, 0);
        if (from != null) {
            start = InventoryRules.startOfDayUtc(from);
        }
        if (to != null) {
            end = InventoryRules.startOfDayUtc(to.plusDays(1));
        }
        String searchQuery = "";
        if (query != null) {
            searchQuery = query.trim();
        }
        PageRequest pageable = pageRequest(page, size, "transactionId");
        Page<InventoryTransaction> result =
                inventoryTransactionRepository.search(
                        shopId, variantId, batchId, transactionType, searchQuery, BusinessCodes.parse("LOT", searchQuery), BusinessCodes.parse("INV", searchQuery), start, end, pageable);
        if (result.getNumber() >= Math.max(1, result.getTotalPages())) {
            pageable = pageable.withPage(Math.max(0, result.getTotalPages() - 1));
            result =
                    inventoryTransactionRepository.search(
                            shopId, variantId, batchId, transactionType, searchQuery, BusinessCodes.parse("LOT", searchQuery), BusinessCodes.parse("INV", searchQuery), start, end, pageable);
        }
        Map<Long, InventoryTransactionRepository.StockBalance> balances = new HashMap<>();
        if (!result.isEmpty()) {
            List<Long> transactionIds = result.getContent().stream()
                    .map(InventoryTransaction::getTransactionId).toList();
            for (InventoryTransactionRepository.StockBalance balance :
                    inventoryTransactionRepository.findStockBalances(shopId, transactionIds)) {
                balances.put(balance.getTransactionId(), balance);
            }
        }
        return result.map(transaction -> toJournalResponse(transaction, balances.get(transaction.getTransactionId())));
    }

    private JournalResponse toJournalResponse(
            InventoryTransaction transaction, InventoryTransactionRepository.StockBalance balance) {
        ProductVariant variant =
                productVariantRepository.findById(transaction.getVariantId()).orElseThrow();
        Product product = productRepository.findById(variant.getProductId()).orElseThrow();
        String orderCode = null;
        if ("ORDER_ITEM".equals(transaction.getReferenceType()) && transaction.getReferenceId() != null) {
            orderCode = orderCodesForLines(List.of(transaction.getReferenceId()), product.getShopId())
                    .get(transaction.getReferenceId());
        }
        String actorName = "Hệ thống";
        if (transaction.getCreatedBy() != null) {
            User actor = userRepository.findById(transaction.getCreatedBy()).orElse(null);
            actorName = actor == null || !hasText(actor.getFullName()) ? "Người dùng" : actor.getFullName();
        }
        return new JournalResponse(transaction, product.getName(), variant.getSku(), orderCode, actorName,
                balance == null ? null : balance.getQuantityOnHandAfter(),
                balance == null ? null : balance.getReservedQuantityAfter());
    }

    private StockTransactionType parseStockType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return StockTransactionType.valueOf(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new CatalogException("Loại biến động kho không hợp lệ.");
        }
    }

    @Override
    public Page<AuditEvent> audit(Authentication authentication, int page, int size) {
        Long shopId = shopService.getManagedShopId(authentication);
        return auditPage(shopId, page, size);
    }

    private Page<AuditEvent> auditPage(Long shopId, int page, int size) {
        PageRequest pageable = pageRequest(page, size, "auditId");
        Page<AuditEvent> result = auditEventRepository.history(shopId, pageable);
        if (result.getNumber() >= Math.max(1, result.getTotalPages())) {
            pageable = pageable.withPage(Math.max(0, result.getTotalPages() - 1));
            result = auditEventRepository.history(shopId, pageable);
        }
        return result;
    }

    @Override
    public Page<AuditResponse> activityHistory(Authentication authentication, int page, int size) {
        return activityHistory(authentication, new ActivityHistoryFilter(), page, size);
    }

    @Override
    public Page<AuditResponse> activityHistory(Authentication authentication, ActivityHistoryFilter filters, int page, int size) {
        Long shopId = shopService.getManagedShopId(authentication);
        filters.normalize();
        String query = filters.getQ();
        String pattern = "%" + query.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        LocalDateTime from = filters.getFrom() == null ? LocalDateTime.of(1900, 1, 1, 0, 0)
                : InventoryRules.startOfDayUtc(filters.getFrom());
        LocalDateTime until = filters.getTo() == null ? LocalDateTime.of(9998, 1, 1, 0, 0)
                : InventoryRules.startOfDayUtc(filters.getTo().plusDays(1));
        PageRequest pageable = pageRequest(page, size, "auditId");
        Page<AuditEvent> events = auditEventRepository.searchHistory(shopId, filters.getAction(), filters.getEntity(),
                query.isEmpty(), pattern, BusinessCodes.parse("PRD", query), BusinessCodes.parse("LOT", query), from, until, pageable);
        if (events.getNumber() >= Math.max(1, events.getTotalPages())) {
            events = auditEventRepository.searchHistory(shopId, filters.getAction(), filters.getEntity(),
                    query.isEmpty(), pattern, BusinessCodes.parse("PRD", query), BusinessCodes.parse("LOT", query),
                    from, until, pageable.withPage(Math.max(0, events.getTotalPages() - 1)));
        }
        Set<Long> productIds = new HashSet<>();
        Set<Long> variantIds = new HashSet<>();
        Set<Long> batchIds = new HashSet<>();
        Set<Long> actorIds = new HashSet<>();
        for (AuditEvent event : events) {
            if (event.getActorId() != null) {
                actorIds.add(event.getActorId());
            }
            if (event.getEntityId() == null) {
                continue;
            }
            if ("PRODUCT".equals(event.getEntityType())) {
                productIds.add(event.getEntityId());
            } else if ("VARIANT".equals(event.getEntityType())) {
                variantIds.add(event.getEntityId());
            } else if ("BATCH".equals(event.getEntityType())) {
                batchIds.add(event.getEntityId());
            }
        }

        Map<Long, InventoryBatch> batches = new HashMap<>();
        if (!batchIds.isEmpty()) {
            for (InventoryBatch batch : inventoryBatchRepository.findAllById(batchIds)) {
                batches.put(batch.getBatchId(), batch);
                variantIds.add(batch.getVariantId());
            }
        }
        Map<Long, ProductVariant> variants = new HashMap<>();
        if (!variantIds.isEmpty()) {
            for (ProductVariant variant : productVariantRepository.findAllById(variantIds)) {
                variants.put(variant.getVariantId(), variant);
                productIds.add(variant.getProductId());
            }
        }
        Map<Long, Product> products = new HashMap<>();
        if (!productIds.isEmpty()) {
            for (Product product : productRepository.findAllById(productIds)) {
                if (shopId.equals(product.getShopId())) {
                    products.put(product.getProductId(), product);
                }
            }
        }
        Map<Long, String> actors = new HashMap<>();
        if (!actorIds.isEmpty()) {
            for (User user : userRepository.findAllById(actorIds)) {
                if (hasText(user.getFullName())) {
                    actors.put(user.getUserId(), user.getFullName().trim());
                }
            }
            Set<Long> actorsWithoutNames = new HashSet<>(actorIds);
            actorsWithoutNames.removeAll(actors.keySet());
            if (!actorsWithoutNames.isEmpty()) {
                for (ShopMember member : shopMemberRepository.findByShopIdAndUserIdIn(shopId, actorsWithoutNames)) {
                    String roleLabel = shopRoleLabel(member.getMemberRole());
                    if (roleLabel != null) {
                        actors.putIfAbsent(member.getUserId(), roleLabel);
                    }
                }
            }
        }
        return events.map(event -> new AuditResponse(
                event,
                auditActionLabel(event),
                auditEntityLabel(event, products, variants, batches),
                event.getActorId() == null ? null : actors.getOrDefault(event.getActorId(), "Người dùng")));
    }

    private String auditEntityLabel(
            AuditEvent event,
            Map<Long, Product> products,
            Map<Long, ProductVariant> variants,
            Map<Long, InventoryBatch> batches) {
        if ("PRODUCT".equals(event.getEntityType())) {
            return auditProductName(products.get(event.getEntityId()));
        }
        if ("VARIANT".equals(event.getEntityType())) {
            ProductVariant variant = variants.get(event.getEntityId());
            if (variant == null) {
                return "Phân loại";
            }
            String label = auditProductName(products.get(variant.getProductId()));
            if (hasText(variant.getVariantName())) {
                label += " · " + variant.getVariantName().trim();
            }
            if (hasText(variant.getSku()) && !variant.getSku().equals(variant.getVariantName())) {
                label += " · " + variant.getSku().trim();
            }
            return label;
        }
        if ("BATCH".equals(event.getEntityType())) {
            InventoryBatch batch = batches.get(event.getEntityId());
            ProductVariant variant = batch == null ? null : variants.get(batch.getVariantId());
            String label = variant == null ? "Lô hàng" : auditProductName(products.get(variant.getProductId()));
            if (event.getEntityId() != null) {
                label += " · Lô " + BusinessCodes.format("LOT", event.getEntityId());
            }
            if (variant != null && hasText(variant.getSku())) {
                label += " · " + variant.getSku().trim();
            }
            return label;
        }
        return "Nội dung liên quan";
    }

    private String auditProductName(Product product) {
        return product != null && hasText(product.getName()) ? product.getName().trim() : "Sản phẩm";
    }

    private String shopRoleLabel(String role) {
        if ("OWNER".equals(role)) {
            return "Chủ cửa hàng";
        }
        if ("MANAGER".equals(role)) {
            return "Quản lý cửa hàng";
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String auditActionLabel(AuditEvent event) {
        String action = event.getAction();
        if (action == null) {
            return "Hoạt động khác";
        }
        switch (action) {
            case "CREATE": return "Tạo sản phẩm";
            case "UPDATE": return "VARIANT".equals(event.getEntityType()) ? "Chỉnh sửa phân loại" : "Chỉnh sửa sản phẩm";
            case "PRICE": return "Thay đổi giá";
            case "REMINDER": return "Thay đổi mốc nhắc hạn sử dụng";
            case "STATUS": return "VARIANT".equals(event.getEntityType()) ? "Thay đổi trạng thái phân loại" : "Thay đổi trạng thái sản phẩm";
            case "ADD_SKU": return "Thêm phân loại";
            case "RESTOCK": return "Nhập kho";
            case "EDIT": return "Chỉnh sửa lô";
            case "ADJUSTMENT": return "Điều chỉnh tồn kho";
            case "DISPOSE": return "Ghi nhận hủy hàng hết hạn";
            case "BLOCK": return "Khóa lô";
            case "UNBLOCK": return "Mở khóa lô";
            case "MARKDOWN": return "Thiết lập giảm giá lô";
            case "CANCEL_MARKDOWN": return "Dừng giảm giá lô";
            case "RESERVE": return "Giữ hàng cho đơn";
            case "RELEASE": return "Giải phóng hàng giữ";
            case "SALE": return "Xuất bán";
            default: return "Hoạt động khác";
        }
    }

    private PageRequest pageRequest(int page, int size, String idProperty) {
        int pageSize = PageSizes.normalize(size);
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt", idProperty);
        int pageIndex = Math.min(Math.max(1, page) - 1, Integer.MAX_VALUE / pageSize);
        return PageRequest.of(pageIndex, pageSize, sort);
    }

    @Override
    public BatchResponse batch(Authentication authentication, Long batchId) {
        InventoryBatch batch = getOwnedBatch(authentication, batchId, false);
        LocalDate date = today();
        BatchMarkdown markdown = findCurrentMarkdown(batchId, date);
        ProductVariant variant =
                productVariantRepository.findById(batch.getVariantId()).orElseThrow();
        Product product = productRepository.findById(variant.getProductId()).orElseThrow();
        BatchResponse response = new BatchResponse(
                batch,
                available(batch, date),
                expired(batch, date),
                nearExpiry(batch, date, product.getExpiryWarningDays()),
                markdown,
                stockAllocationRepository.findByBatchIdOrderByAllocationIdDesc(batchId),
                product.getName(),
                variant.getSku(),
                variant.getUnit(),
                InventoryRules.markdownEligible(batch, date),
                product.getExpiryWarningDays(),
                InventoryRules.daysUntilExpiry(batch, date),
                markdownAllowed(product, variant));
        response.setVariantName(variant.getVariantName());
        List<Long> lineIds = new ArrayList<>();
        for (StockAllocation allocation : response.getAllocations()) lineIds.add(allocation.getOrderItemId());
        response.setAllocationOrderCodes(orderCodesForLines(lineIds, product.getShopId()));
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
            if (shopId.equals(order.getShopId()) && order.getOrder() != null) {
                references.put(order.getShopOrderId(), order.getOrder().getOrderCode());
            }
        }
        for (Map.Entry<Long, Long> line : shopOrderIds.entrySet()) {
            String code = references.get(line.getValue());
            if (code != null) orderCodes.put(line.getKey(), code);
        }
        return orderCodes;
    }

    @Override
    public String batchCode(Long batchId) {
        return BusinessCodes.format("LOT", batchId);
    }

    private boolean markdownAllowed(Product product, ProductVariant variant) {
        return productAvailabilityService.canSellVariant(product.getShopId(), variant);
    }

    private BatchMarkdown findCurrentMarkdown(Long batchId, LocalDate date) {
        for (BatchMarkdown markdown :
                batchMarkdownRepository.findByBatchIdAndStatus(batchId, MarkdownStatus.ACTIVE)) {
            if (!markdown.getEndDate().isBefore(date) && !markdown.getStartDate().isAfter(date)) {
                return markdown;
            }
        }
        return null;
    }

    private ProductVariant getOwnedVariant(
            Authentication authentication, Long variantId, boolean lock) {
        Long shopId = shopService.getManagedShopId(authentication);
        ProductVariant snapshot =
                productVariantRepository
                        .findById(variantId)
                        .orElseThrow(
                                () ->
                                        new AccessDeniedException(
                                                "Bạn không có quyền truy cập phân loại này."));
        Product product;
        if (lock) {
            product =
                    productRepository
                            .lockOwned(snapshot.getProductId(), shopId)
                            .orElseThrow(
                                    () ->
                                            new AccessDeniedException(
                                                    "Phân loại không thuộc cửa hàng của bạn."));
            inventoryLockHelper.refresh(product);
            ProductVariant variant = productVariantRepository.lockById(variantId).orElseThrow();
            return inventoryLockHelper.refresh(variant);
        }
        product =
                productRepository
                        .findById(snapshot.getProductId())
                        .orElseThrow(
                                () -> new AccessDeniedException("Phân loại không thuộc cửa hàng của bạn."));
        if (!product.getShopId().equals(shopId)) {
            throw new AccessDeniedException("Phân loại không thuộc cửa hàng của bạn.");
        }
        return snapshot;
    }

    private InventoryBatch getOwnedBatch(
            Authentication authentication, Long batchId, boolean lock) {
        InventoryBatch snapshot =
                inventoryBatchRepository
                        .findById(batchId)
                        .orElseThrow(
                                () -> new AccessDeniedException("Bạn không có quyền truy cập lô hàng này."));
        // Keep lock ordering: product, variant, then batch.
        getOwnedVariant(authentication, snapshot.getVariantId(), lock);
        if (!lock) {
            return snapshot;
        }
        InventoryBatch batch = inventoryBatchRepository.lockById(batchId).orElseThrow();
        return inventoryLockHelper.refresh(batch);
    }
}
