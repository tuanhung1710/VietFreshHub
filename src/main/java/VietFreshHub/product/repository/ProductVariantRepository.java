package VietFreshHub.product.repository;

import VietFreshHub.product.entity.ProductVariant;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
    List<ProductVariant> findByProductIdOrderByVariantId(Long productId);

    List<ProductVariant> findByProductIdInOrderByVariantId(List<Long> ids);

    boolean existsBySkuIgnoreCase(String sku);

    @Query(
            "select count(v) from ProductVariant v, Product p where v.productId=p.productId and"
                + " p.shopId=:shop and p.status<>VietFreshHub.product.enums.ProductStatus.DELETED"
                + " and v.status<>VietFreshHub.product.enums.VariantStatus.DELETED")
    long countVisibleInShop(Long shop);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from ProductVariant v where v.variantId=:id")
    Optional<ProductVariant> lockById(Long id);

    @Query(value = "select count(*) from dbo.order_items where variant_id=:id", nativeQuery = true)
    long orderReferenceCount(Long id);
}
