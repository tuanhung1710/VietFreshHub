package VietFreshHub.inventory.repository;

import VietFreshHub.inventory.entity.AuditEvent;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
    /**
     * Lọc lịch sử theo hành động, đối tượng, ngày, nội dung hoặc mã PRD, LOT.
     * Mọi kết quả đều giới hạn trong sản phẩm, phân loại và lô của cửa hàng.
     */
    // entityId có ý nghĩa theo entityType, nên từng nhánh phải nối đúng đường sản phẩm, SKU hoặc
    // lô.
    // Điều kiện quyền của cả ba nhánh được gom trong ngoặc trước khi kết hợp các bộ lọc còn lại.
    @Query(
            """
            select a from AuditEvent a where
              ((a.entityType='PRODUCT' and exists(select p from Product p where p.productId=a.entityId and p.shopId=:shop)) or
               (a.entityType='VARIANT' and exists(select p from Product p, ProductVariant v where v.variantId=a.entityId and v.productId=p.productId and p.shopId=:shop)) or
               (a.entityType='BATCH' and exists(select p from Product p, ProductVariant v, InventoryBatch b where b.batchId=a.entityId and b.variantId=v.variantId and v.productId=p.productId and p.shopId=:shop)))
              and (:action='' or a.action=:action)
              and (:entity='' or a.entityType=:entity)
              and a.createdAt>=:from and a.createdAt<:until
              and (:emptyQuery=true or lower(a.reason) like :pattern escape '!'
                or (a.entityType='PRODUCT' and exists(
                    select p from Product p where p.productId=a.entityId and
                     (lower(p.name) like :pattern escape '!' or p.productId=:productCode or exists(
                        select v from ProductVariant v where v.productId=p.productId and
                         (lower(v.sku) like :pattern escape '!' or lower(v.variantName) like :pattern escape '!')))))
                or (a.entityType='VARIANT' and exists(
                    select p from Product p, ProductVariant v where v.variantId=a.entityId and v.productId=p.productId and
                     (lower(p.name) like :pattern escape '!' or p.productId=:productCode or
                      lower(v.sku) like :pattern escape '!' or lower(v.variantName) like :pattern escape '!')))
                or (a.entityType='BATCH' and exists(
                    select p from Product p, ProductVariant v, InventoryBatch b where b.batchId=a.entityId and b.variantId=v.variantId and v.productId=p.productId and
                     (lower(p.name) like :pattern escape '!' or p.productId=:productCode or b.batchId=:batchCode or
                      lower(v.sku) like :pattern escape '!' or lower(v.variantName) like :pattern escape '!'))))
            """)
    Page<AuditEvent> searchHistory(
            Long shop,
            String action,
            String entity,
            boolean emptyQuery,
            String pattern,
            Long productCode,
            Long batchCode,
            LocalDateTime from,
            LocalDateTime until,
            Pageable page);
}
