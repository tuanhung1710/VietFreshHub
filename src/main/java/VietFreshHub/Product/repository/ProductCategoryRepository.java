package VietFreshHub.Product.repository;
import VietFreshHub.Product.entity.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ProductCategoryRepository extends JpaRepository<ProductCategory,ProductCategory.Key> {
    List<ProductCategory> findByIdProductId(Long productId);
    void deleteByIdProductId(Long productId);
}

