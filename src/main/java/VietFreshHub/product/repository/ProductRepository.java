package VietFreshHub.product.repository;

import VietFreshHub.product.entity.Product;
import VietFreshHub.product.enums.ProductStatus;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, ProductManagementRepository {
    /** Khóa ghi sản phẩm theo ID, chỉ trả bản ghi thuộc đúng cửa hàng. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.productId=:id and p.shopId=:shop")
    Optional<Product> lockOwned(Long id, Long shop);

    /** So sánh tên trong shop, bỏ khoảng trắng hai đầu và không phân biệt hoa thường. */
    @Query(value = """
            select count(*) from dbo.products
            where shop_id = :shopId
              and ltrim(rtrim(name)) collate Latin1_General_100_CI_AS
                  = :name collate Latin1_General_100_CI_AS
              and (:excludedProductId is null or product_id <> :excludedProductId)
            """, nativeQuery = true)
    long countDuplicateNames(@Param("shopId") Long shopId, @Param("name") String name,
            @Param("excludedProductId") Long excludedProductId);

    /** Đếm sản phẩm trong cửa hàng, loại trừ trạng thái được chỉ định. */
    long countByShopIdAndStatusNot(Long shopId, ProductStatus status);

}
