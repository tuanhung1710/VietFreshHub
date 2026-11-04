package VietFreshHub.inventory.service;

import static VietFreshHub.inventory.rule.InventoryRules.require;
import static VietFreshHub.inventory.rule.InventoryRules.today;

import VietFreshHub.inventory.dto.InventoryCatalogFilter;
import VietFreshHub.inventory.dto.InventoryCatalogRow;
import VietFreshHub.inventory.dto.InventoryCatalogSummary;
import VietFreshHub.product.repository.InventoryCatalogRepository;
import VietFreshHub.shop.service.ShopService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryCatalogService {
    private static final int SUGGESTION_LIMIT = 10;
    private final InventoryCatalogRepository repository;
    private final ShopService shopService;

    public record CatalogPage(Page<InventoryCatalogRow> rows, InventoryCatalogSummary summary, LocalDate date) { }

    public CatalogPage list(Authentication authentication, InventoryCatalogFilter filters, int page, int size) {
        Long shop = shopService.getManagedShopId(authentication);
        normalize(filters);
        if (filters.getVariantId() != null && !repository.ownsVariant(shop, filters.getVariantId())) {
            throw new AccessDeniedException("Phân loại không thuộc cửa hàng của bạn.");
        }
        LocalDate date = today();
        return new CatalogPage(repository.search(shop, filters, date, page, size),
                repository.summary(shop, date, filters.isArchived()), date);
    }

    public List<InventoryCatalogRow> restockChoices(Authentication authentication, String query) {
        Long shop = shopService.getManagedShopId(authentication);
        LocalDate date = today();
        var filters = new InventoryCatalogFilter();
        filters.setQ(text(query));
        var choices = new ArrayList<InventoryCatalogRow>();
        if (!filters.getQ().isEmpty()) {
            choices.addAll(repository.suggestions(shop, filters, date, SUGGESTION_LIMIT));
        }
        if (choices.size() < SUGGESTION_LIMIT) {
            filters.setView("sku");
            for (InventoryCatalogRow sku : repository.suggestions(shop, filters, date, SUGGESTION_LIMIT)) {
                if (choices.stream().noneMatch(choice -> choice.variantId().equals(sku.variantId()))) {
                    choices.add(sku);
                    if (choices.size() == SUGGESTION_LIMIT) break;
                }
            }
        }
        return choices;
    }

    private void normalize(InventoryCatalogFilter filters) {
        filters.setQ(text(filters.getQ()));
        filters.setStock(text(filters.getStock()));
        filters.setExpiry(text(filters.getExpiry()));
        filters.setView(text(filters.getView()));
        switch (filters.getStock()) {
            case "near", "expired", "expiry" -> {
                filters.setExpiry(filters.getStock().equals("expiry") ? "attention" : filters.getStock());
                filters.setStock("");
            }
            case "archived" -> {
                filters.setArchived(true);
                filters.setStock("");
            }
        }
        require(List.of("", "available", "enough", "low", "empty").contains(filters.getStock()), "Mức tồn kho không hợp lệ.");
        require(List.of("", "valid", "near", "expired", "attention").contains(filters.getExpiry()), "Hạn sử dụng không hợp lệ.");
        require(List.of("lots", "sku").contains(filters.getView()), "Chế độ xem kho không hợp lệ.");
        LocalDate from = filters.getReceivedFrom();
        LocalDate to = filters.getReceivedTo();
        require((from == null || from.getYear() >= 1 && from.getYear() <= 9999)
                && (to == null || to.getYear() >= 1 && to.getYear() <= 9999), "Ngày nhập kho không hợp lệ.");
        require(from == null || to == null || !from.isAfter(to), "Ngày nhập từ phải trước hoặc bằng ngày nhập đến.");
    }

    private String text(String value) {
        String normalized = value == null ? "" : value.trim();
        require(normalized.length() <= 200, "Bộ lọc không được vượt quá 200 ký tự.");
        return normalized;
    }
}
