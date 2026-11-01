package VietFreshHub.catalog.repository;

import VietFreshHub.catalog.entity.CatalogProduct;
import VietFreshHub.catalog.entity.CatalogCategory;
import VietFreshHub.catalog.service.CatalogCategoryPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

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

    @org.springframework.data.jpa.repository.Query("select p.status as status, count(p) as productCount from CatalogProduct p where p.status <> 'DELETED' group by p.status")
    java.util.List<ProductStatusCount> countProductsByStatus();

    interface ProductWithoutOrders {
        String getProductName();
        String getShopName();
    }

    @org.springframework.data.jpa.repository.Query(value = """
            select p.name as productName, s.shopName as shopName from CatalogProduct p join p.shop s
            where p.status <> 'DELETED' and not exists
                (select i.orderItemId from VietFreshHub.order.entity.OrderItem i where i.variant.product = p)
            order by p.name, p.productId
            """, countQuery = """
            select count(p) from CatalogProduct p
            where p.status <> 'DELETED' and not exists
                (select i.orderItemId from VietFreshHub.order.entity.OrderItem i where i.variant.product = p)
            """)
    org.springframework.data.domain.Page<ProductWithoutOrders> findProductsWithoutOrders(
            org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query("""
            select count(distinct v.variantId)
            from CatalogProduct p join p.variants v
            where p.status <> 'DELETED' and v.status <> 'DELETED'
              and exists (select b.batchId from VietFreshHub.inventory.entity.InventoryBatch b
                  where b.variantId = v.variantId
                    and b.status = VietFreshHub.inventory.enums.BatchStatus.ACTIVE
                    and (b.expiryDate is null or b.expiryDate >= :today)
                    and b.quantityOnHand > b.reservedQuantity)
            """)
    long countVariantsWithAvailableStock(@org.springframework.data.repository.query.Param("today") java.time.LocalDate today);

    @org.springframework.data.jpa.repository.Query(value = """
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
            """, countQuery = """
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

    @org.springframework.data.jpa.repository.Query(value = "select p from CatalogProduct p join fetch p.shop s "
            + "where p.status = 'ACTIVE' and p.approvalStatus = 'APPROVED' and s.status = 'ACTIVE' "
            + "and exists (select c.categoryId from p.categories c where c.categoryId in :categoryIds) "
            + "and exists (select v.variantId from CatalogVariant v where v.product = p and v.status in ('ACTIVE','OUT_OF_STOCK')) "
            + "and (:keyword = '' or locate(lower(:keyword), lower(p.name)) > 0)",
            countQuery = "select count(p) from CatalogProduct p join p.shop s where p.status = 'ACTIVE' and p.approvalStatus = 'APPROVED' "
            + "and s.status = 'ACTIVE' and exists (select c.categoryId from p.categories c where c.categoryId in :categoryIds) "
            + "and exists (select v.variantId from CatalogVariant v where v.product = p and v.status in ('ACTIVE','OUT_OF_STOCK')) "
            + "and (:keyword = '' or locate(lower(:keyword), lower(p.name)) > 0)")
    org.springframework.data.domain.Page<CatalogProduct> findVisibleCatalogForCategories(@org.springframework.data.repository.query.Param("keyword") String keyword,
                                                                   @org.springframework.data.repository.query.Param("categoryIds") java.util.Set<Long> categoryIds,
                                                                   org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query("select c from CatalogCategory c")
    java.util.List<CatalogCategory> findCatalogCategories();

    default org.springframework.data.domain.Page<CatalogProduct> findVisibleCatalog(String keyword, org.springframework.data.domain.Pageable pageable) {
        var ids = CatalogCategoryPolicy.activeIds(findCatalogCategories());
        return ids.isEmpty() ? org.springframework.data.domain.Page.empty(pageable) : findVisibleCatalogForCategories(keyword, ids, pageable);
    }

    default org.springframework.data.domain.Page<CatalogProduct> findVisibleCatalog(org.springframework.data.domain.Pageable pageable) {
        return findVisibleCatalog("", pageable);
    }

    java.util.List<CatalogProduct> findAllBySlug(String slug);

    Optional<CatalogProduct> findByName(String name);
    Optional<CatalogProduct> findBySlug(String slug);
}
