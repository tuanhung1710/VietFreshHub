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
    @Query(
            """
            select a from AuditEvent a where
             (a.entityType='PRODUCT' and exists(select p from Product p where p.productId=a.entityId and p.shopId=:shop)) or
             (a.entityType='VARIANT' and exists(select p from Product p, ProductVariant v where v.variantId=a.entityId and v.productId=p.productId and p.shopId=:shop)) or
             (a.entityType='BATCH' and exists(select p from Product p, ProductVariant v, InventoryBatch b where b.batchId=a.entityId and b.variantId=v.variantId and v.productId=p.productId and p.shopId=:shop))
            """)
    Page<AuditEvent> history(Long shop, Pageable page);

    @Query("""
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
    Page<AuditEvent> searchHistory(Long shop, String action, String entity, boolean emptyQuery,
            String pattern, Long productCode, Long batchCode, LocalDateTime from, LocalDateTime until, Pageable page);
}
