package VietFreshHub.Product.repository;
import VietFreshHub.Product.entity.Product;
import VietFreshHub.Product.enums.ProductStatus;
import VietFreshHub.Product.enums.ApprovalStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
public interface ProductRepository extends JpaRepository<Product, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.productId=:id and p.shopId=:shop")
    Optional<Product> lockOwned(Long id, Long shop);
    boolean existsByShopIdAndSlug(Long shopId, String slug);
    long countByShopIdAndStatusNot(Long shopId,ProductStatus status);
    @Query("""
        select p from Product p where p.shopId=:shop
        and (:archived=true or p.status<>VietFreshHub.Product.enums.ProductStatus.DELETED)
        and (:productStatus is null or p.status=:productStatus)
        and (:statusApproval is null or p.approvalStatus=:statusApproval)
        and (:approval is null or p.approvalStatus=:approval)
        and (:createdFrom is null or p.createdAt>=:createdFrom)
        and (:createdTo is null or p.createdAt<:createdTo)
        and (:business='' or
            (:business='published' and p.status=VietFreshHub.Product.enums.ProductStatus.ACTIVE and p.approvalStatus=VietFreshHub.Product.enums.ApprovalStatus.APPROVED
                and exists(select v from ProductVariant v where v.productId=p.productId and v.status in (VietFreshHub.Product.enums.VariantStatus.ACTIVE,VietFreshHub.Product.enums.VariantStatus.OUT_OF_STOCK))
                and not exists(select v from ProductVariant v where v.productId=p.productId and v.status<>VietFreshHub.Product.enums.VariantStatus.DELETED and (v.price is null or v.price<10000 or v.price<>floor(v.price)))
                and exists(select pc from ProductCategory pc where pc.id.productId=p.productId and pc.id.categoryId in :activeCategories)) or
            (:business='unpublished' and (p.status<>VietFreshHub.Product.enums.ProductStatus.ACTIVE or p.approvalStatus<>VietFreshHub.Product.enums.ApprovalStatus.APPROVED
                or not exists(select v from ProductVariant v where v.productId=p.productId and v.status in (VietFreshHub.Product.enums.VariantStatus.ACTIVE,VietFreshHub.Product.enums.VariantStatus.OUT_OF_STOCK))
                or exists(select v from ProductVariant v where v.productId=p.productId and v.status<>VietFreshHub.Product.enums.VariantStatus.DELETED and (v.price is null or v.price<10000 or v.price<>floor(v.price)))
                or not exists(select pc from ProductCategory pc where pc.id.productId=p.productId and pc.id.categoryId in :activeCategories))))
        and (:category is null or exists(select pc from ProductCategory pc where pc.id.productId=p.productId and pc.id.categoryId=:category))
        and (lower(p.name) like lower(concat('%',:q,'%')) or lower(p.slug) like lower(concat('%',:q,'%'))
             or exists(select v from ProductVariant v where v.productId=p.productId and
               (lower(v.sku) like lower(concat('%',:q,'%')) or lower(v.variantName) like lower(concat('%',:q,'%')) or exists(select b from InventoryBatch b where b.variantId=v.variantId and lower(b.batchCode) like lower(concat('%',:q,'%'))))))
        and (:stock='' or
          (:stock='archived' and exists(select b from InventoryBatch b, ProductVariant v where b.variantId=v.variantId and v.productId=p.productId and b.quantityOnHand>0 and (p.status=VietFreshHub.Product.enums.ProductStatus.DELETED or v.status=VietFreshHub.Product.enums.VariantStatus.DELETED))) or
          (:stock='expired' and exists(select b from InventoryBatch b, ProductVariant v where b.variantId=v.variantId and v.productId=p.productId and b.quantityOnHand>0 and b.expiryDate<:today)) or
          (:stock='near' and exists(select b from InventoryBatch b, ProductVariant v where b.variantId=v.variantId and v.productId=p.productId and b.quantityOnHand>b.reservedQuantity and b.status=VietFreshHub.Inventory.enums.BatchStatus.ACTIVE and b.expiryDate between :today and :near)) or
          (:stock='low' and exists(select v from ProductVariant v where v.productId=p.productId and v.status<>VietFreshHub.Product.enums.VariantStatus.DELETED and
            (select coalesce(sum(b.quantityOnHand-b.reservedQuantity),0) from InventoryBatch b where b.variantId=v.variantId and b.status=VietFreshHub.Inventory.enums.BatchStatus.ACTIVE and (b.expiryDate is null or b.expiryDate>=:today)) <= v.lowStockThreshold)))
        """)
    Page<Product> search(Long shop,String q,Long category,ProductStatus productStatus,ApprovalStatus statusApproval,boolean archived,String stock,
        String business,ApprovalStatus approval,LocalDateTime createdFrom,LocalDateTime createdTo,List<Long> activeCategories,
        LocalDate today,LocalDate near,Pageable pageable);
}
