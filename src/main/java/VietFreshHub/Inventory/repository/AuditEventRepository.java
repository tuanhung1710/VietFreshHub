package VietFreshHub.Inventory.repository;
import VietFreshHub.Inventory.entity.AuditEvent;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
public interface AuditEventRepository extends JpaRepository<AuditEvent,Long> {
    @Query("""
      select a from AuditEvent a where
       (a.entityType='PRODUCT' and exists(select p from Product p where p.productId=a.entityId and p.shopId=:shop)) or
       (a.entityType='VARIANT' and exists(select p from Product p, ProductVariant v where v.variantId=a.entityId and v.productId=p.productId and p.shopId=:shop)) or
       (a.entityType='BATCH' and exists(select p from Product p, ProductVariant v, InventoryBatch b where b.batchId=a.entityId and b.variantId=v.variantId and v.productId=p.productId and p.shopId=:shop))
      """)
    Page<AuditEvent> history(Long shop, Pageable page);
}

