package VietFreshHub.product.repository;

import VietFreshHub.product.entity.ProductCategory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductCategoryRepository
        extends JpaRepository<ProductCategory, ProductCategory.Key> {
    List<ProductCategory> findByIdProductId(Long productId);

    void deleteByIdProductId(Long productId);
}
