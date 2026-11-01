package VietFreshHub.product.repository;

import VietFreshHub.product.entity.ProductCategory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductCategoryRepository
        extends JpaRepository<ProductCategory, ProductCategory.Key> {
    /** Lấy các liên kết giữa sản phẩm và danh mục. */
    List<ProductCategory> findByIdProductId(Long productId);

    /** Xóa các liên kết danh mục của sản phẩm trước khi thay danh mục mới. */
    void deleteByIdProductId(Long productId);
}
