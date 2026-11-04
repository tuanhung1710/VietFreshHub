package VietFreshHub.product.repository;

import VietFreshHub.product.entity.ProductVariant;
import VietFreshHub.product.enums.VariantStatus;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
    /** Lấy các phân loại của sản phẩm theo ID tăng dần, gồm cả phân loại ngừng kinh doanh. */
    List<ProductVariant> findByProductIdOrderByVariantId(Long productId);

    /** Tải chung phân loại của nhiều sản phẩm theo ID tăng dần. */
    List<ProductVariant> findByProductIdInOrderByVariantId(List<Long> ids);

    /** Đếm phân loại của sản phẩm, loại trừ trạng thái được chỉ định. */
    long countByProductIdAndStatusNot(Long productId, VariantStatus status);

    /** Kiểm tra SKU đã tồn tại trên hệ thống, không phân biệt hoa thường. */
    boolean existsBySkuIgnoreCase(String sku);

    /** Khóa ghi phân loại theo ID trước khi thay đổi trạng thái hoặc xử lý kho. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from ProductVariant v where v.variantId=:id")
    Optional<ProductVariant> lockById(Long id);

    /** Đếm dòng đơn tham chiếu phân loại để chặn thay đổi đơn vị bán đã có lịch sử. */
    @Query(value = "select count(*) from dbo.order_items where variant_id=:id", nativeQuery = true)
    long orderReferenceCount(Long id);
}
