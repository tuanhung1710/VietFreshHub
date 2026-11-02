package VietFreshHub.inventory.repository;

import VietFreshHub.inventory.entity.InventoryBatch;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryBatchRepository extends JpaRepository<InventoryBatch, Long> {
    interface InventoryByShopUnit {
        String getShopName();
        String getProductName();
        String getVariantName();
        String getUnit();
        Long getQuantityOnHand();
        Long getAvailableQuantity();
    }

    interface InventoryByProductVariant {
        String getProductName();
        String getVariantName();
        String getUnit();
        Long getQuantityOnHand();
        Long getAvailableQuantity();
    }

    interface ExpiringInventory {
        String getShopName();
        String getProductName();
        String getVariantName();
        String getUnit();
        java.time.LocalDate getExpiryDate();
        Long getAvailableQuantity();
    }

    @Query("""
            select sh.shopName as shopName, p.name as productName, v.variantName as variantName, v.unit as unit,
                   sum(b.quantityOnHand) as quantityOnHand,
                   sum(case when b.quantityOnHand > b.reservedQuantity
                       then b.quantityOnHand - b.reservedQuantity else 0 end) as availableQuantity
            from InventoryBatch b, VietFreshHub.product.entity.ProductVariant v,
                 VietFreshHub.product.entity.Product p, VietFreshHub.shop.entity.Shop sh
            where v.variantId = b.variantId and p.productId = v.productId and sh.shopId = p.shopId
              and v.status <> VietFreshHub.product.enums.VariantStatus.DELETED
              and b.status = VietFreshHub.inventory.enums.BatchStatus.ACTIVE
              and (b.expiryDate is null or b.expiryDate >= :today)
              and p.status <> VietFreshHub.product.enums.ProductStatus.DELETED
            group by sh.shopName, p.productId, p.name, v.variantId, v.variantName, v.unit
            order by sh.shopName, p.name, v.variantName
            """)
    List<InventoryByShopUnit> summarizeByShopAndUnit(@org.springframework.data.repository.query.Param("today") java.time.LocalDate today);

    @Query("""
            select p.name as productName, v.variantName as variantName, v.unit as unit,
                   sum(b.quantityOnHand) as quantityOnHand,
                   sum(case when b.quantityOnHand > b.reservedQuantity
                       then b.quantityOnHand - b.reservedQuantity else 0 end) as availableQuantity
            from InventoryBatch b, VietFreshHub.product.entity.ProductVariant v,
                 VietFreshHub.product.entity.Product p
            where v.variantId = b.variantId and p.productId = v.productId
              and v.status <> VietFreshHub.product.enums.VariantStatus.DELETED
              and b.status = VietFreshHub.inventory.enums.BatchStatus.ACTIVE
              and (b.expiryDate is null or b.expiryDate >= :today)
              and p.status <> VietFreshHub.product.enums.ProductStatus.DELETED
            group by p.productId, p.name, v.variantId, v.variantName, v.unit
            order by p.name, v.variantName
            """)
    List<InventoryByProductVariant> summarizeByProductVariant(@org.springframework.data.repository.query.Param("today") java.time.LocalDate today);

    @Query(value = """
            select sh.shopName as shopName, p.name as productName, v.variantName as variantName,
                   v.unit as unit, b.expiryDate as expiryDate,
                   (b.quantityOnHand - b.reservedQuantity) as availableQuantity
            from InventoryBatch b, VietFreshHub.product.entity.ProductVariant v,
                 VietFreshHub.product.entity.Product p, VietFreshHub.shop.entity.Shop sh
            where v.variantId = b.variantId and p.productId = v.productId and sh.shopId = p.shopId
              and b.status = VietFreshHub.inventory.enums.BatchStatus.ACTIVE
              and b.expiryDate >= :today and b.quantityOnHand > b.reservedQuantity
              and timestampdiff(day, :today, b.expiryDate) <= coalesce(p.expiryWarningDays, 3)
              and v.status <> VietFreshHub.product.enums.VariantStatus.DELETED
              and p.status <> VietFreshHub.product.enums.ProductStatus.DELETED
            order by b.expiryDate, sh.shopName, p.name, v.variantName
            """, countQuery = """
            select count(b.batchId)
            from InventoryBatch b, VietFreshHub.product.entity.ProductVariant v,
                 VietFreshHub.product.entity.Product p, VietFreshHub.shop.entity.Shop sh
            where v.variantId = b.variantId and p.productId = v.productId and sh.shopId = p.shopId
              and b.status = VietFreshHub.inventory.enums.BatchStatus.ACTIVE
              and b.expiryDate >= :today and b.quantityOnHand > b.reservedQuantity
              and timestampdiff(day, :today, b.expiryDate) <= coalesce(p.expiryWarningDays, 3)
              and v.status <> VietFreshHub.product.enums.VariantStatus.DELETED
              and p.status <> VietFreshHub.product.enums.ProductStatus.DELETED
            """)
    org.springframework.data.domain.Page<ExpiringInventory> findApproachingExpiry(
            @org.springframework.data.repository.query.Param("today") java.time.LocalDate today,
            org.springframework.data.domain.Pageable pageable);

    List<InventoryBatch> findByVariantIdOrderByExpiryDateAscBatchIdAsc(Long variantId);

    /** Tải chung lô của nhiều phân loại theo hạn sử dụng và ID tăng dần. */
    List<InventoryBatch> findByVariantIdInOrderByExpiryDateAscBatchIdAsc(List<Long> ids);

    /** Khóa ghi lô theo ID trước khi cập nhật tồn, lượng giữ hoặc thông tin lô. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from InventoryBatch b where b.batchId=:id")
    Optional<InventoryBatch> lockById(Long id);
}
