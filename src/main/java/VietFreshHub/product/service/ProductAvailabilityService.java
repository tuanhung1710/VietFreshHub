package VietFreshHub.product.service;

import VietFreshHub.inventory.rule.InventoryRules;
import VietFreshHub.product.dto.ProductManagementState;
import VietFreshHub.product.entity.Category;
import VietFreshHub.product.entity.ProductVariant;
import VietFreshHub.product.repository.CategoryRepository;
import VietFreshHub.product.repository.ProductRepository;
import VietFreshHub.product.validator.CategoryValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductAvailabilityService {
    private final ProductRepository products;
    private final CategoryRepository categories;
    private final CategoryValidator categoryValidator;

    public List<Long> activeCategoryIds() {
        List<Long> ids = new ArrayList<>();
        for (Category category : categories.findAllByOrderByNameAsc()) {
            if (categoryValidator.isActiveCategoryChain(category.getCategoryId())) ids.add(category.getCategoryId());
        }
        return ids;
    }

    public Map<Long, ProductManagementState> states(Long shop, List<Long> productIds) {
        return products.managementStates(shop, productIds, activeCategoryIds(), InventoryRules.today());
    }

    public boolean canSellVariant(Long shop, ProductVariant variant) {
        return products.canSellVariant(shop, variant.getVariantId(), activeCategoryIds(), InventoryRules.today());
    }
}
