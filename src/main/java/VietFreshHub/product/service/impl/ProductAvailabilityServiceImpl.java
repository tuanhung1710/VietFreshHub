package VietFreshHub.product.service.impl;

import VietFreshHub.inventory.rule.InventoryRules;
import VietFreshHub.product.dto.ProductManagementState;
import VietFreshHub.product.entity.Category;
import VietFreshHub.product.entity.ProductVariant;
import VietFreshHub.product.repository.CategoryRepository;
import VietFreshHub.product.repository.ProductRepository;
import VietFreshHub.product.service.ProductAvailabilityService;
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
public class ProductAvailabilityServiceImpl implements ProductAvailabilityService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final CategoryValidator categoryValidator;

    @Override
    public List<Long> activeCategoryIds() {
        List<Long> ids = new ArrayList<>();
        for (Category category : categoryRepository.findAllByOrderByNameAsc()) {
            if (categoryValidator.isActiveCategoryChain(category.getCategoryId())) {
                ids.add(category.getCategoryId());
            }
        }
        return ids;
    }

    @Override
    public Map<Long, ProductManagementState> states(Long shopId, List<Long> productIds) {
        return productRepository.managementStates(
                shopId, productIds, activeCategoryIds(), InventoryRules.today());
    }

    @Override
    public boolean canSellVariant(Long shopId, ProductVariant variant) {
        return productRepository.canSellVariant(
                shopId, variant.getVariantId(), activeCategoryIds(), InventoryRules.today());
    }
}
