package VietFreshHub.inventory.service;

import VietFreshHub.inventory.dto.AllocationResponse;
import VietFreshHub.inventory.dto.AllocationResponse.ProcessingEvent;
import VietFreshHub.inventory.entity.InventoryOrderLine;
import VietFreshHub.inventory.entity.InventoryTransaction;
import VietFreshHub.inventory.entity.StockAllocation;
import VietFreshHub.inventory.enums.StockTransactionType;
import VietFreshHub.inventory.repository.InventoryOrderLineRepository;
import VietFreshHub.inventory.repository.InventoryTransactionRepository;
import VietFreshHub.order.entity.ShopOrder;
import VietFreshHub.order.repository.ShopOrderRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BatchAllocationHistoryService {
    private static final List<StockTransactionType> HISTORY_TYPES = List.of(
            StockTransactionType.RESERVE, StockTransactionType.RELEASE,
            StockTransactionType.SALE, StockTransactionType.RETURN);

    private final InventoryOrderLineRepository orderLines;
    private final ShopOrderRepository shopOrders;
    private final InventoryTransactionRepository transactions;

    /** Lô đã được kiểm tra quyền; chỉ nhận dòng đơn thuộc đúng SKU và cửa hàng. */
    public List<AllocationResponse> load(
            Long batchId, Long variantId, Long shopId, String unit, List<StockAllocation> allocations) {
        if (allocations.isEmpty()) {
            return List.of();
        }
        Map<Long, InventoryOrderLine> lines = loadOrderLines(variantId, allocations);
        if (lines.isEmpty()) {
            return List.of();
        }
        Map<Long, ShopOrder> orders = loadOrdersInShop(shopId, lines);
        if (lines.isEmpty()) {
            return List.of();
        }
        Map<Long, List<InventoryTransaction>> histories = loadProcessingHistory(batchId, variantId, lines);

        List<AllocationResponse> result = new ArrayList<>();
        for (StockAllocation allocation : allocations) {
            InventoryOrderLine line = lines.get(allocation.getOrderItemId());
            if (line == null || !batchId.equals(allocation.getBatchId())) {
                continue;
            }
            ShopOrder order = orders.get(line.getShopOrderId());
            List<InventoryTransaction> history = histories.getOrDefault(line.getOrderItemId(), List.of());
            result.add(toResponse(allocation, line, order, history, unit));
        }
        return result;
    }

    private Map<Long, InventoryOrderLine> loadOrderLines(Long variantId, List<StockAllocation> allocations) {
        List<Long> lineIds = allocations.stream().map(StockAllocation::getOrderItemId).distinct().toList();
        Map<Long, InventoryOrderLine> lines = new HashMap<>();
        for (InventoryOrderLine line : orderLines.findAllById(lineIds)) {
            if (variantId.equals(line.getVariantId())) {
                lines.put(line.getOrderItemId(), line);
            }
        }

        return lines;
    }

    /** Loại dòng đơn ngoài shop trước khi tải lịch sử xử lý. */
    private Map<Long, ShopOrder> loadOrdersInShop(Long shopId, Map<Long, InventoryOrderLine> lines) {
        List<Long> shopOrderIds = lines.values().stream()
                .map(InventoryOrderLine::getShopOrderId).distinct().toList();
        Map<Long, ShopOrder> orders = new HashMap<>();
        for (ShopOrder order : shopOrders.findAllById(shopOrderIds)) {
            if (shopId.equals(order.getShopId())) {
                orders.put(order.getShopOrderId(), order);
            }
        }
        lines.values().removeIf(line -> !orders.containsKey(line.getShopOrderId()));

        return orders;
    }

    private Map<Long, List<InventoryTransaction>> loadProcessingHistory(
            Long batchId, Long variantId, Map<Long, InventoryOrderLine> lines) {
        // Đọc một lần cho cả lô, rồi ghép theo dòng đơn; không trộn lịch sử của lô hoặc đơn khác.
        Map<Long, List<InventoryTransaction>> histories = new HashMap<>();
        for (InventoryTransaction event : transactions.findOrderProcessingHistory(batchId, variantId, HISTORY_TYPES)) {
            if (lines.containsKey(event.getReferenceId())) {
                histories.computeIfAbsent(event.getReferenceId(), ignored -> new ArrayList<>()).add(event);
            }
        }
        Comparator<InventoryTransaction> chronological = Comparator
                .comparing(InventoryTransaction::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(InventoryTransaction::getTransactionId, Comparator.nullsLast(Comparator.naturalOrder()));
        histories.values().forEach(history -> history.sort(chronological));

        return histories;
    }

    private AllocationResponse toResponse(StockAllocation allocation, InventoryOrderLine line,
            ShopOrder order, List<InventoryTransaction> history, String unit) {
        LocalDateTime updatedAt = history.stream().map(InventoryTransaction::getCreatedAt)
                .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
        List<ProcessingEvent> events = history.stream().map(event -> new ProcessingEvent(
                event.getCreatedAt(), description(event, unit), note(event))).toList();
        String orderCode = order.getOrder() == null ? "Đơn hàng" : order.getOrder().getOrderCode();
        return new AllocationResponse(allocation.getAllocationId(), line.getOrderItemId(), orderCode,
                line.getQuantity(), unit, allocation.getStatus(), updatedAt, events);

    }

    private String description(InventoryTransaction event, String unit) {
        String actualUnit = unit == null || unit.isBlank() ? "đơn vị" : unit;
        String quantity = event.getQuantity() == null ? "hàng "
                : Math.abs((long) event.getQuantity()) + " " + actualUnit + " ";
        return switch (event.getTransactionType()) {
            case RESERVE -> "Giữ " + quantity + "cho đơn hàng.";
            case RELEASE -> "Hủy giữ " + quantity + "cho đơn hàng.";
            case SALE -> "Xuất kho " + quantity + "để giao hàng.";
            case RETURN -> "Nhập lại " + quantity + "được trả về.";
            default -> throw new IllegalArgumentException("Loại giao dịch không thuộc lịch sử xử lý đơn hàng.");
        };
    }

    private String note(InventoryTransaction event) {
        return event.getNote() == null || event.getNote().isBlank() ? null : event.getNote().trim();
    }
}
