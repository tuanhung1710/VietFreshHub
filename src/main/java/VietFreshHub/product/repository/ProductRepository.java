package VietFreshHub.product.repository;

import VietFreshHub.product.entity.Product;
import VietFreshHub.product.enums.ApprovalStatus;
import VietFreshHub.product.enums.ProductStatus;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, ProductManagementRepository {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.productId=:id and p.shopId=:shop")
    Optional<Product> lockOwned(Long id, Long shop);

    boolean existsByShopIdAndSlug(Long shopId, String slug);

    long countByShopIdAndStatusNot(Long shopId, ProductStatus status);

    @Query(
            """
            select p from Product p where p.shopId=:shop
            and (:archived=true or p.status<>VietFreshHub.product.enums.ProductStatus.DELETED)
            and (:productStatus is null or p.status=:productStatus)
            and (:statusApproval is null or p.approvalStatus=:statusApproval)
            and (:approval is null or p.approvalStatus=:approval)
            and (:createdFrom is null or p.createdAt>=:createdFrom)
            and (:createdTo is null or p.createdAt<:createdTo)
            and (:category is null or exists(select pc from ProductCategory pc where pc.id.productId=p.productId and pc.id.categoryId=:category))
            and ((:productCode is null and :batchCode is null and
                 (lower(p.name) like lower(concat('%',:q,'%')) or lower(p.slug) like lower(concat('%',:q,'%'))
                 or exists(select v from ProductVariant v where v.productId=p.productId and
                   (lower(v.sku) like lower(concat('%',:q,'%')) or lower(v.variantName) like lower(concat('%',:q,'%'))))))
                 or (:productCode is not null and p.productId=:productCode)
                 or exists(select b from InventoryBatch b, ProductVariant bv where b.variantId=bv.variantId and bv.productId=p.productId and :batchCode is not null and b.batchId=:batchCode))
            and (:stock='' or
              (:stock='archived' and exists(select b from InventoryBatch b, ProductVariant v where b.variantId=v.variantId and v.productId=p.productId and b.quantityOnHand>0 and (p.status=VietFreshHub.product.enums.ProductStatus.DELETED or v.status=VietFreshHub.product.enums.VariantStatus.DELETED))) or
              (:stock='expired' and exists(select b from InventoryBatch b, ProductVariant v where b.variantId=v.variantId and v.productId=p.productId and b.quantityOnHand>0 and b.expiryDate<:today)) or
              (:stock='near' and exists(select b from InventoryBatch b, ProductVariant v where b.variantId=v.variantId and v.productId=p.productId and b.quantityOnHand>b.reservedQuantity and b.status=VietFreshHub.inventory.enums.BatchStatus.ACTIVE and b.expiryDate>=:today and timestampdiff(day,:today,b.expiryDate)<=coalesce(p.expiryWarningDays,3))) or
              (:stock='expiry' and exists(select b from InventoryBatch b, ProductVariant v where b.variantId=v.variantId and v.productId=p.productId and b.quantityOnHand>0 and timestampdiff(day,:today,b.expiryDate)<=coalesce(p.expiryWarningDays,3))) or
              (:stock='low' and exists(select v from ProductVariant v where v.productId=p.productId and v.status<>VietFreshHub.product.enums.VariantStatus.DELETED and
                (select coalesce(sum(b.quantityOnHand-b.reservedQuantity),0) from InventoryBatch b where b.variantId=v.variantId and b.status=VietFreshHub.inventory.enums.BatchStatus.ACTIVE and (b.expiryDate is null or b.expiryDate>=:today)) <= v.lowStockThreshold)))
            """)
    Page<Product> search(
            Long shop,
            String q,
            Long productCode,
            Long batchCode,
            Long category,
            ProductStatus productStatus,
            ApprovalStatus statusApproval,
            boolean archived,
            String stock,
            ApprovalStatus approval,
            LocalDateTime createdFrom,
            LocalDateTime createdTo,
            LocalDate today,
            Pageable pageable);
}
