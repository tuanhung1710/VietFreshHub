package VietFreshHub.product.service;

import static VietFreshHub.inventory.rule.InventoryRules.require;
import static VietFreshHub.inventory.rule.InventoryRules.today;
import VietFreshHub.common.BusinessCodes;
import VietFreshHub.inventory.dto.BatchResponse;
import VietFreshHub.inventory.dto.InventoryCatalogFilter;
import VietFreshHub.inventory.rule.InventoryRules;
import VietFreshHub.product.dto.ProductCatalogFilter;
import VietFreshHub.product.dto.ProductResponse;
import VietFreshHub.product.dto.VariantResponse;
import VietFreshHub.product.entity.Product;
import VietFreshHub.product.enums.VariantStatus;
import VietFreshHub.product.repository.ProductRepository;
import VietFreshHub.product.util.CatalogPagination;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Thực hiện truy vấn và dựng danh sách sau khi service đã xác thực shop, bộ lọc. */
@Component
@RequiredArgsConstructor
public class ProductCatalogQueries {
    private final ProductRepository productRepository;
    private final ProductCatalogReader catalogReader;

    public Page<ProductResponse> searchInventory(Long shopId, InventoryCatalogFilter filters, int page, int size) {
        PageRequest pageRequest = CatalogPagination.request(page, size);
        Page<Product> productPage = findInventoryPage(shopId, filters, pageRequest);
        Set<Long> matchingVariantIds = findMatchingVariantIds(shopId, filters, productPage);

        Page<ProductResponse> responses = catalogReader.toPage(productPage);
        for (ProductResponse productResponse : responses) {
            populateInventoryView(productResponse, filters, matchingVariantIds);
        }
        return responses;
    }

    public Page<ProductResponse> searchManagement(Long shopId, ProductCatalogFilter filters,
            List<Long> activeCategories, int page, int size) {
        LocalDate createdFrom = filters.getCreatedFrom();
        LocalDate createdTo = filters.getCreatedTo();
        PageRequest pageable = CatalogPagination.request(page, size);

        Page<Product> result = productRepository.searchManagement(shopId, filters,
                startOfDayOrNull(createdFrom), startOfNextDayOrNull(createdTo), activeCategories, today(), pageable);

        if (result.getNumber() >= Math.max(1, result.getTotalPages())) {
            // Đưa về trang đầu khi trang cũ không còn tồn tại, chẳng hạn sau khi đổi bộ lọc.
            result = productRepository.searchManagement(shopId, filters,
                    startOfDayOrNull(createdFrom), startOfNextDayOrNull(createdTo), activeCategories, today(), pageable.withPage(0));
        }
        return catalogReader.toPage(result);
    }

    /** Quay về trang đầu khi bộ lọc làm trang yêu cầu không còn tồn tại. */
    private Page<Product> findInventoryPage(
            Long shopId, InventoryCatalogFilter filters, PageRequest pageRequest) {
        Page<Product> productPage = productRepository.searchInventory(shopId, filters, today(), pageRequest);
        boolean pageIsOutOfRange = productPage.getNumber() >= Math.max(1, productPage.getTotalPages());
        if (pageIsOutOfRange) {
            return productRepository.searchInventory(shopId, filters, today(), pageRequest.withPage(0));
        }
        return productPage;
    }

    /** Chỉ lấy SKU khớp bộ lọc trong các sản phẩm của trang hiện tại. */
    private Set<Long> findMatchingVariantIds(
            Long shopId, InventoryCatalogFilter filters, Page<Product> productPage) {
        List<Long> productIds = productPage.getContent().stream()
                .map(Product::getProductId)
                .toList();
        return new HashSet<>(productRepository.matchingInventoryVariants(
                shopId, filters, today(), productIds));
    }

    /** Giữ tổng số phân loại trong phạm vi lưu trữ, rồi chọn các dòng con khớp bộ lọc. */
    private void populateInventoryView(
            ProductResponse productResponse, InventoryCatalogFilter filters, Set<Long> matchingVariantIds) {
        int inventoryVariantTotal = 0;
        for (VariantResponse variantResponse : productResponse.getVariants()) {
            boolean includeInTotal = filters.isArchived()
                    || variantResponse.getVariant().getStatus() != VariantStatus.DELETED;
            if (includeInTotal) {
                inventoryVariantTotal++;
            }
        }
        productResponse.setInventoryVariantTotal(inventoryVariantTotal);

        List<VariantResponse> matchingVariants = productResponse.getVariants().stream()
                .filter(variantResponse -> matchingVariantIds.contains(variantResponse.getVariant().getVariantId()))
                .toList();
        productResponse.setInventoryVariants(matchingVariants);

        filterInventoryBatches(matchingVariants, filters);
        updateInventorySummary(productResponse, matchingVariants);
    }

    /** Chỉ lọc danh sách lô hiển thị; tổng tồn của SKU vẫn lấy từ toàn bộ lô. */
    private void filterInventoryBatches(List<VariantResponse> matchingVariants, InventoryCatalogFilter filters) {
        Long requestedBatchId = BusinessCodes.parse("LOT", filters.getQ());
        String expiryFilter = filters.getExpiry();
        boolean filterByExpiry = !expiryFilter.isEmpty();
        if (requestedBatchId == null && !filterByExpiry) {
            return;
        }

        for (VariantResponse variantResponse : matchingVariants) {
            List<BatchResponse> matchingBatches = variantResponse.getBatches().stream()
                    .filter(batchResponse -> inventoryBatchMatches(batchResponse, requestedBatchId, expiryFilter))
                    .toList();
            variantResponse.setMatchedBatches(matchingBatches);
        }
    }

    /** Tính số lô và cảnh báo từ phần dữ liệu đang được hiển thị trong kho. */
    private void updateInventorySummary(ProductResponse productResponse, List<VariantResponse> matchingVariants) {
        int visibleBatchCount = 0;
        boolean hasExpiredStock = false;
        boolean hasNearExpiryStock = false;

        for (VariantResponse variantResponse : matchingVariants) {
            List<BatchResponse> visibleBatches = variantResponse.getInventoryBatches();
            visibleBatchCount += visibleBatches.size();

            for (BatchResponse batchResponse : visibleBatches) {
                if (batchResponse.getBatch().getQuantityOnHand() > 0) {
                    hasExpiredStock = hasExpiredStock || batchResponse.isExpired();
                    hasNearExpiryStock = hasNearExpiryStock || batchResponse.isNearExpiry();
                }
            }
        }

        productResponse.setBatchCount(visibleBatchCount);
        productResponse.setExpired(hasExpiredStock);
        productResponse.setNearExpiry(hasNearExpiryStock);
    }

    /** Kiểm tra mã LOT và điều kiện hạn sử dụng; khi lọc theo hạn chỉ xét lô còn hàng. */
    private boolean inventoryBatchMatches(BatchResponse batchResponse, Long requestedBatchId, String expiryFilter) {
        if (requestedBatchId != null && !requestedBatchId.equals(batchResponse.getBatch().getBatchId())) {
            return false;
        }
        if (expiryFilter.isEmpty()) {
            return true;
        }
        if (batchResponse.getBatch().getQuantityOnHand() <= 0) {
            return false;
        }

        return switch (expiryFilter) {
            case "near" -> batchResponse.isNearExpiry();
            case "expired" -> batchResponse.isExpired();
            case "valid" -> !batchResponse.isExpired() && !batchResponse.isNearExpiry();
            case "attention" -> batchResponse.isExpired() || batchResponse.isNearExpiry();
            default -> false;
        };
    }

    /** Đổi đầu ngày tại Việt Nam sang UTC để lọc dữ liệu; giữ null khi không chọn ngày. */
    private LocalDateTime startOfDayOrNull(LocalDate date) {
        if (date == null) {
            return null;
        }
        return InventoryRules.startOfDayUtc(date);
    }

    /** Lấy đầu ngày kế tiếp theo UTC làm mốc kết thúc không bao gồm, để lọc đủ ngày đã chọn. */
    private LocalDateTime startOfNextDayOrNull(LocalDate date) {
        if (date == null) {
            return null;
        }
        require(date.getYear() >= 1 && date.getYear() <= 9999,
                "Ngày lọc vượt giới hạn cho phép.");
        return InventoryRules.startOfDayUtc(date.plusDays(1));
    }
}
