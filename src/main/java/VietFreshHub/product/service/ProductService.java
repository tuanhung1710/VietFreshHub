package VietFreshHub.product.service;

import VietFreshHub.product.dto.ActionRequest;
import VietFreshHub.product.dto.BulkProductActionRequest;
import VietFreshHub.product.dto.ProductCatalogFilter;
import VietFreshHub.product.dto.CatalogSummary;
import VietFreshHub.product.dto.ProductRequest;
import VietFreshHub.product.dto.ProductResponse;
import VietFreshHub.product.dto.ProductTabSummary;
import VietFreshHub.product.dto.VariantActionRequest;
import VietFreshHub.product.dto.VariantRequest;
import VietFreshHub.product.dto.VariantResponse;
import VietFreshHub.product.entity.Category;
import VietFreshHub.product.entity.PriceHistory;

import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;

import java.time.LocalDate;
import java.util.List;

public interface ProductService {

    Page<ProductResponse> searchInventory(Authentication authentication,
            VietFreshHub.inventory.dto.InventoryCatalogFilter filters, int page, int size);

    Page<ProductResponse> searchManagement(Authentication authentication, ProductCatalogFilter filters, int page, int size);

    void changeStatuses(Authentication authentication, BulkProductActionRequest form);

    Page<ProductResponse> searchManagement(Authentication authentication, String query, Long categoryId,
            String tab, LocalDate createdFrom, LocalDate createdTo, int page, int size);

    ProductTabSummary productTabSummary(Authentication authentication);
    VietFreshHub.inventory.dto.InventoryAttentionSummary inventoryAttentionSummary(Authentication authentication);

    Page<ProductResponse> search(
            Authentication authentication,
            String query,
            Long categoryId,
            String status,
            boolean archived,
            String stock,
            int page,
            int size);

    Page<ProductResponse> search(
            Authentication authentication,
            String query,
            Long categoryId,
            String status,
            boolean archived,
            String stock,
            String business,
            String approval,
            LocalDate createdFrom,
            LocalDate createdTo,
            int page,
            int size);

    CatalogSummary catalogSummary(Authentication authentication);

    ProductResponse get(Authentication authentication, Long productId);

    VariantResponse getVariant(Authentication authentication, Long productId, Long variantId);

    List<Category> categories();

    List<String> units();

    ProductRequest editForm(Authentication authentication, Long productId);

    VariantRequest variantForm(Authentication authentication, Long productId, Long variantId);

    Long create(Authentication authentication, ProductRequest form);

    void update(Authentication authentication, Long productId, ProductRequest form);

    void changeStatus(Authentication authentication, Long productId, ActionRequest form);

    void addVariant(Authentication authentication, Long productId, VariantRequest form);

    void updateVariant(
            Authentication authentication, Long productId, Long variantId, VariantRequest form);

    void changeVariantStatus(
            Authentication authentication,
            Long productId,
            Long variantId,
            VariantActionRequest form);

    Page<PriceHistory> priceHistory(
            Authentication authentication, Long productId, Long variantId, int page);
}
