package VietFreshHub.product.repository;

import VietFreshHub.product.entity.ProductImage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {
    /** Lấy ảnh sản phẩm và ảnh phân loại theo thứ tự hiển thị rồi ID tăng dần. */
    List<ProductImage> findByProductIdOrderBySortOrderAscImageIdAsc(Long productId);
}
