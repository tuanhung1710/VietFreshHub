package VietFreshHub.product.repository;

import VietFreshHub.product.dto.ProductManagementState;
import VietFreshHub.product.dto.ProductCatalogFilter;
import VietFreshHub.product.dto.ProductTabSummary;
import VietFreshHub.product.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface ProductManagementRepository {
    Page<Product> searchInventory(Long shop, VietFreshHub.inventory.dto.InventoryCatalogFilter filters,
            LocalDate today, Pageable page);
    List<Long> matchingInventoryVariants(Long shop, VietFreshHub.inventory.dto.InventoryCatalogFilter filters,
            LocalDate today, List<Long> products);
    Page<Product> searchManagement(Long shop, ProductCatalogFilter filters,
            LocalDateTime from, LocalDateTime to, List<Long> categories, LocalDate today, Pageable page);
    Page<Product> searchManagement(Long shop, String query, Long category, String tab,
            LocalDateTime from, LocalDateTime to, List<Long> categories, LocalDate today, Pageable page);
    ProductTabSummary managementSummary(Long shop, List<Long> categories, LocalDate today);
    Map<Long, ProductManagementState> managementStates(Long shop, List<Long> ids,
            List<Long> categories, LocalDate today);
    boolean canSellVariant(Long shop, Long variant, List<Long> categories, LocalDate today);
    VietFreshHub.inventory.dto.InventoryAttentionSummary inventoryAttentionSummary(Long shop, LocalDate today);
}
