package VietFreshHub.Product.repository;
import VietFreshHub.Product.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ProductImageRepository extends JpaRepository<ProductImage,Long> {
    List<ProductImage> findByProductIdOrderBySortOrderAscImageIdAsc(Long productId);
}

