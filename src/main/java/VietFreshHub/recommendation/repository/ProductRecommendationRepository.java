package VietFreshHub.recommendation.repository;

import VietFreshHub.catalog.entity.CatalogProduct;
import VietFreshHub.recommendation.dto.ProductRecommendation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.*;

/** TV3 read-only recommendations; does not change catalog or inventory. */
public interface ProductRecommendationRepository extends Repository<CatalogProduct, Long> {
    @EntityGraph(attributePaths={"shop", "categories"})
    Optional<CatalogProduct> findByProductId(Long productId);

    @Query("""
            select p from CatalogProduct p join fetch p.shop s
            where p.productId <> :sourceId and p.status='ACTIVE' and p.approvalStatus='APPROVED'
              and s.status='ACTIVE' and s.availabilityStatus='OPEN'
              and exists(select c.categoryId from p.categories c where c.categoryId in :activeIds)
              and exists(select v.variantId from CatalogVariant v where v.product=p and v.status='ACTIVE'
                and exists(select b.batchId from CheckoutInventoryBatch b where b.variant=v and b.status='ACTIVE'
                  and (b.expiryDate is null or b.expiryDate>=:today) and b.quantityOnHand>b.reservedQuantity))
            order by case when exists(select c.categoryId from p.categories c where c.categoryId in :relatedIds) then 0 else 1 end,
                     case when s.shopId=:shopId then 0 else 1 end, p.productId desc
            """)
    List<CatalogProduct> findCandidates(@Param("sourceId") Long sourceId, @Param("shopId") Long shopId,
            @Param("activeIds") Set<Long> activeIds, @Param("relatedIds") Set<Long> relatedIds,
            @Param("today") LocalDate today, Pageable pageable);

    @Query("""
            select new VietFreshHub.recommendation.dto.ProductRecommendation(
                p.productId,v.variantId,p.name,v.variantName,p.shop.shopName,v.thumbnailUrl,v.price)
            from CatalogVariant v join v.product p
            where p.productId in :productIds and v.status='ACTIVE'
              and exists(select b.batchId from CheckoutInventoryBatch b where b.variant=v and b.status='ACTIVE'
                and (b.expiryDate is null or b.expiryDate>=:today) and b.quantityOnHand>b.reservedQuantity)
            order by p.productId,v.price,v.variantId
            """)
    List<ProductRecommendation> findAvailableVariants(@Param("productIds") List<Long> productIds, @Param("today") LocalDate today);
}
