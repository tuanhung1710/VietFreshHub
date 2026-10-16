package VietFreshHub.product.validator;

import VietFreshHub.product.entity.Category;
import VietFreshHub.product.entity.ProductCategory;
import VietFreshHub.product.enums.CategoryStatus;
import VietFreshHub.product.repository.CategoryRepository;
import VietFreshHub.product.repository.ProductCategoryRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class CategoryValidator {

    private final CategoryRepository categoryRepository;
    private final ProductCategoryRepository productCategoryRepository;

    public boolean hasActiveCategory(Long productId) {
        for (ProductCategory link : productCategoryRepository.findByIdProductId(productId)) {
            if (isActiveCategoryChain(link.getId().getCategoryId())) {
                return true;
            }
        }
        return false;
    }

    public boolean isActiveCategoryChain(Long categoryId) {
        Set<Long> visitedIds = new HashSet<>();
        Long currentId = categoryId;

        while (currentId != null) {
            if (!visitedIds.add(currentId) || visitedIds.size() > 100) {
                return false;
            }
            Category category = categoryRepository.findById(currentId).orElse(null);
            if (category == null || category.getStatus() != CategoryStatus.ACTIVE) {
                return false;
            }
            currentId = category.getParentCategoryId();
        }
        return !visitedIds.isEmpty();
    }
}
