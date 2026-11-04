package VietFreshHub.inventory.service;

import static VietFreshHub.inventory.rule.InventoryRules.require;
import static VietFreshHub.inventory.rule.InventoryRules.today;

import VietFreshHub.inventory.dto.InventoryCatalogFilter;
import VietFreshHub.inventory.dto.InventoryCatalogRow;
import VietFreshHub.inventory.dto.InventoryCatalogSummary;
import VietFreshHub.inventory.repository.InventoryCatalogRepository;
import VietFreshHub.shop.service.ShopService;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Đọc danh sách lô/tổng tồn SKU và gợi ý nhập tiếp trong phạm vi cửa hàng đang quản lý. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryCatalogService {
    private static final int SUGGESTION_LIMIT = 10;
    private final InventoryCatalogRepository repository;
    private final ShopService shopService;

    public record CatalogPage(
            Page<InventoryCatalogRow> rows, InventoryCatalogSummary summary, LocalDate date) {}

    /** Kiểm tra quyền, chuẩn hóa bộ lọc rồi lấy dữ liệu trang và tổng quan tại cùng ngày nghiệp vụ. */
    public CatalogPage list(
            Authentication authentication, InventoryCatalogFilter filters, int page, int size) {
        Long shopId = shopService.getManagedShopId(authentication);
        normalize(filters);
        if (filters.getVariantId() != null
                && !repository.ownsVariant(shopId, filters.getVariantId())) {
            throw new AccessDeniedException("Phân loại không thuộc cửa hàng của bạn.");
        }
        LocalDate date = today();
        return new CatalogPage(
                repository.search(shopId, filters, date, page, size),
                repository.summary(shopId, date, filters.isArchived()),
                date);
    }

    /** Ưu tiên lô khớp từ khóa, sau đó bổ sung SKU chưa có trong kết quả, tối đa 10 lựa chọn. */
    public List<InventoryCatalogRow> restockChoices(Authentication authentication, String query) {
        Long shopId = shopService.getManagedShopId(authentication);
        LocalDate date = today();
        var filters = new InventoryCatalogFilter();
        filters.setQ(text(query));
        var choices = new ArrayList<InventoryCatalogRow>();
        if (!filters.getQ().isEmpty()) {
            choices.addAll(repository.suggestions(shopId, filters, date, SUGGESTION_LIMIT));
        }
        if (choices.size() < SUGGESTION_LIMIT) {
            filters.setView("sku");
            appendDistinctSkus(
                    choices, repository.suggestions(shopId, filters, date, SUGGESTION_LIMIT));
        }
        return choices;
    }

    private void appendDistinctSkus(
            List<InventoryCatalogRow> choices, List<InventoryCatalogRow> skus) {
        for (InventoryCatalogRow sku : skus) {
            boolean alreadySuggested =
                    choices.stream().anyMatch(choice -> choice.variantId().equals(sku.variantId()));
            if (!alreadySuggested) {
                choices.add(sku);
                if (choices.size() == SUGGESTION_LIMIT) {
                    break;
                }
            }
        }
    }

    private void normalize(InventoryCatalogFilter filters) {
        filters.setQ(text(filters.getQ()));
        filters.setStock(text(filters.getStock()));
        filters.setExpiry(text(filters.getExpiry()));
        filters.setView(text(filters.getView()));
        normalizeLegacyStockFilter(filters);
        validateStockAndExpiryFilters(filters);
        validateReceivedDateRange(filters.getReceivedFrom(), filters.getReceivedTo());
    }

    /** Giữ các liên kết kho đã lưu trước đây khi bộ lọc mức tồn và hạn sử dụng còn dùng chung. */
    private void normalizeLegacyStockFilter(InventoryCatalogFilter filters) {
        switch (filters.getStock()) {
            case "near", "expired", "expiry" -> {
                filters.setExpiry(
                        filters.getStock().equals("expiry") ? "attention" : filters.getStock());
                filters.setStock("");
            }
            case "archived" -> {
                filters.setArchived(true);
                filters.setStock("");
            }
        }
    }

    private void validateStockAndExpiryFilters(InventoryCatalogFilter filters) {
        require(
                List.of("", "available", "enough", "low", "empty").contains(filters.getStock()),
                "Mức tồn kho không hợp lệ.");
        require(
                List.of("", "valid", "near", "expired", "attention").contains(filters.getExpiry()),
                "Hạn sử dụng không hợp lệ.");
        require(List.of("lots", "sku").contains(filters.getView()), "Chế độ xem kho không hợp lệ.");
    }

    private void validateReceivedDateRange(LocalDate from, LocalDate to) {
        require(
                (from == null || from.getYear() >= 1 && from.getYear() <= 9999)
                        && (to == null || to.getYear() >= 1 && to.getYear() <= 9999),
                "Ngày nhập kho không hợp lệ.");
        require(
                from == null || to == null || !from.isAfter(to),
                "Ngày nhập từ phải trước hoặc bằng ngày nhập đến.");
    }

    private String text(String value) {
        String normalized = value == null ? "" : value.trim();
        require(normalized.length() <= 200, "Bộ lọc không được vượt quá 200 ký tự.");
        return normalized;
    }
}
