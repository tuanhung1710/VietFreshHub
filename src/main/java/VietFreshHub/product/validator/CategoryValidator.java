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

    /** Kiểm tra sản phẩm có ít nhất một danh mục với toàn bộ chuỗi danh mục cha còn hoạt động. */
    public boolean hasActiveCategory(Long productId) {
        for (ProductCategory link : productCategoryRepository.findByIdProductId(productId)) {
            if (isActiveCategoryChain(link.getId().getCategoryId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Duyệt từ danh mục lên gốc, yêu cầu mọi cấp đều tồn tại và đang hoạt động.
     * Từ chối chuỗi bị lặp hoặc vượt 100 cấp để tránh duyệt vô hạn.
     */
    public boolean isActiveCategoryChain(Long categoryId) {
        // Ghi các ID đã đi qua để phát hiện dữ liệu cha-con bị trỏ vòng.
        Set<Long> visitedIds = new HashSet<>();
        Long currentId = categoryId;

        while (currentId != null) {
            // Chặn cả chuỗi quá sâu để tránh duyệt không giới hạn khi cấu trúc danh mục có lỗi.
            if (!visitedIds.add(currentId) || visitedIds.size() > 100) {
                return false;
            }
            Category category = categoryRepository.findById(currentId).orElse(null);
            if (category == null || category.getStatus() != CategoryStatus.ACTIVE) {
                return false;
            }
            // Danh mục con chỉ hợp lệ khi toàn bộ các cấp cha đến gốc cũng đang hoạt động.
            currentId = category.getParentCategoryId();
        }
        // ID đầu vào null không đại diện cho một danh mục hợp lệ.
        return !visitedIds.isEmpty();
    }
}
