package VietFreshHub.catalog.repository;

import VietFreshHub.catalog.entity.CatalogCategory;
import VietFreshHub.catalog.entity.CatalogProduct;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CatalogProductRepository extends JpaRepository<CatalogProduct, Long> {
    interface LowStockVariant {
        Long getVariantId();

        String getProductName();

        String getVariantName();

        String getUnit();

        Long getAvailableQuantity();

        Integer getLowStockThreshold();
    }

    interface ProductStatusCount {
        String getStatus();

        Long getProductCount();
    }

    @org.springframework.data.jpa.repository.Query(
            "select p.status as status, count(p) as productCount from CatalogProduct p where p.status <> 'DELETED' group by p.status")
    java.util.List<ProductStatusCount> countProductsByStatus();

    interface ProductWithoutOrders {
        String getProductName();

        String getShopName();
    }

    @org.springframework.data.jpa.repository.Query(
            value =
                    """
            select p.name as productName, s.shopName as shopName from CatalogProduct p join p.shop s
            where p.status <> 'DELETED' and not exists
                (select i.orderItemId from VietFreshHub.order.entity.OrderItem i where i.variant.product = p)
            order by p.name, p.productId
            """,
            countQuery =
                    """
            select count(p) from CatalogProduct p
            where p.status <> 'DELETED' and not exists
                (select i.orderItemId from VietFreshHub.order.entity.OrderItem i where i.variant.product = p)
            """)
    org.springframework.data.domain.Page<ProductWithoutOrders> findProductsWithoutOrders(
            org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query(
            """
            select count(distinct v.variantId)
            from CatalogProduct p join p.variants v
            where p.status <> 'DELETED' and v.status <> 'DELETED'
              and exists (select b.batchId from VietFreshHub.inventory.entity.InventoryBatch b
                  where b.variantId = v.variantId
                    and b.status = VietFreshHub.inventory.enums.BatchStatus.ACTIVE
                    and (b.expiryDate is null or b.expiryDate >= :today)
                    and b.quantityOnHand > b.reservedQuantity)
            """)
    long countVariantsWithAvailableStock(
            @org.springframework.data.repository.query.Param("today") java.time.LocalDate today);

    @org.springframework.data.jpa.repository.Query(
            value =
                    """
            select v.variantId as variantId, p.name as productName, v.variantName as variantName,
                   v.unit as unit,
                   sum(case when b.status = VietFreshHub.inventory.enums.BatchStatus.ACTIVE
                              and (b.expiryDate is null or b.expiryDate >= :today)
                              and b.quantityOnHand > b.reservedQuantity
                            then b.quantityOnHand - b.reservedQuantity else 0 end) as availableQuantity,
                   v.lowStockThreshold as lowStockThreshold
            from CatalogProduct p join p.variants v
            left join VietFreshHub.inventory.entity.InventoryBatch b on b.variantId = v.variantId
            where p.status <> 'DELETED' and v.status <> 'DELETED'
            group by v.variantId, p.name, v.variantName, v.unit, v.lowStockThreshold
            having sum(case when b.status = VietFreshHub.inventory.enums.BatchStatus.ACTIVE
                              and (b.expiryDate is null or b.expiryDate >= :today)
                              and b.quantityOnHand > b.reservedQuantity
                            then b.quantityOnHand - b.reservedQuantity else 0 end) > 0
               and sum(case when b.status = VietFreshHub.inventory.enums.BatchStatus.ACTIVE
                              and (b.expiryDate is null or b.expiryDate >= :today)
                              and b.quantityOnHand > b.reservedQuantity
                            then b.quantityOnHand - b.reservedQuantity else 0 end) <= v.lowStockThreshold
            order by availableQuantity, p.name, v.variantName
            """,
            countQuery =
                    """
            select count(distinct v.variantId)
            from CatalogProduct p join p.variants v
            where p.status <> 'DELETED' and v.status <> 'DELETED'
              and (select coalesce(sum(case when b.status = VietFreshHub.inventory.enums.BatchStatus.ACTIVE
                                               and (b.expiryDate is null or b.expiryDate >= :today)
                                               and b.quantityOnHand > b.reservedQuantity
                                             then b.quantityOnHand - b.reservedQuantity else 0 end), 0)
                   from VietFreshHub.inventory.entity.InventoryBatch b where b.variantId = v.variantId) > 0
              and (select coalesce(sum(case when b.status = VietFreshHub.inventory.enums.BatchStatus.ACTIVE
                                               and (b.expiryDate is null or b.expiryDate >= :today)
                                               and b.quantityOnHand > b.reservedQuantity
                                             then b.quantityOnHand - b.reservedQuantity else 0 end), 0)
                   from VietFreshHub.inventory.entity.InventoryBatch b where b.variantId = v.variantId) <= v.lowStockThreshold
            """)
    org.springframework.data.domain.Page<LowStockVariant> findLowStockVariants(
            @org.springframework.data.repository.query.Param("today") java.time.LocalDate today,
            org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query("select c from CatalogCategory c")
    java.util.List<CatalogCategory> findCatalogCategories();

    java.util.List<CatalogProduct> findAllBySlug(String slug);
}
