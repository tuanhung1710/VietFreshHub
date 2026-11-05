package VietFreshHub.product.service;

import static VietFreshHub.inventory.rule.InventoryRules.require;
import static VietFreshHub.inventory.rule.InventoryRules.today;

import VietFreshHub.common.Pagination;
import VietFreshHub.inventory.rule.InventoryRules;
import VietFreshHub.product.dto.ProductCatalogFilter;
import VietFreshHub.product.dto.ProductResponse;
import VietFreshHub.product.entity.Product;
import VietFreshHub.product.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Thực hiện truy vấn và dựng danh sách sau khi service đã xác thực shop, bộ lọc. */
@Component
@RequiredArgsConstructor
public class ProductCatalogQueries {
    private final ProductRepository productRepository;
    private final ProductCatalogReader catalogReader;

    public Page<ProductResponse> searchManagement(
            Long shopId,
            ProductCatalogFilter filters,
            List<Long> activeCategories,
            int page,
            int size) {
        LocalDate createdFrom = filters.getCreatedFrom();
        LocalDate createdTo = filters.getCreatedTo();
        PageRequest pageable = Pagination.request(page, size);

        Page<Product> result =
                productRepository.searchManagement(
                        shopId,
                        filters,
                        startOfDayOrNull(createdFrom),
                        startOfNextDayOrNull(createdTo),
                        activeCategories,
                        today(),
                        pageable);

        if (result.getNumber() >= Math.max(1, result.getTotalPages())) {
            // Đưa về trang đầu khi trang cũ không còn tồn tại, chẳng hạn sau khi đổi bộ lọc.
            result =
                    productRepository.searchManagement(
                            shopId,
                            filters,
                            startOfDayOrNull(createdFrom),
                            startOfNextDayOrNull(createdTo),
                            activeCategories,
                            today(),
                            pageable.withPage(0));
        }
        return catalogReader.toPage(result);
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
        require(date.getYear() >= 1 && date.getYear() <= 9999, "Ngày lọc vượt giới hạn cho phép.");
        return InventoryRules.startOfDayUtc(date.plusDays(1));
    }
}
