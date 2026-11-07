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
                repository.summary(shopId, date),
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
        filters.setExpiry(text(filters.getExpiry()));
        filters.setView(text(filters.getView()));
        filters.setStock(filters.isSkuView() ? text(filters.getStock()) : "");
        validateFilters(filters);
        validateReceivedDateRange(filters.getReceivedFrom(), filters.getReceivedTo());
    }

    private void validateFilters(InventoryCatalogFilter filters) {
        require(
                List.of("", "enough", "low", "empty").contains(filters.getStock()),
                "Mức tồn kho không hợp lệ.");
        require(
                List.of("", "valid", "near", "expired", "empty", "attention").contains(filters.getExpiry()),
                "Trạng thái lô không hợp lệ.");
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
