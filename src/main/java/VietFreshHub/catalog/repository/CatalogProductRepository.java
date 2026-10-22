package VietFreshHub.catalog.repository;

import VietFreshHub.catalog.entity.CatalogProduct;
import VietFreshHub.catalog.entity.CatalogCategory;
import VietFreshHub.catalog.service.CatalogCategoryPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CatalogProductRepository extends JpaRepository<CatalogProduct, Long> {
    @org.springframework.data.jpa.repository.Query(value = "select p from CatalogProduct p join fetch p.shop s "
            + "where p.status = 'ACTIVE' and p.moderationBlocked = false and s.status = 'ACTIVE' "
            + "and exists (select c.categoryId from p.categories c where c.categoryId in :categoryIds) "
            + "and exists (select v.variantId from CatalogVariant v where v.product = p and v.status in ('ACTIVE','OUT_OF_STOCK')) "
            + "and (:keyword = '' or locate(lower(:keyword), lower(p.name)) > 0)",
            countQuery = "select count(p) from CatalogProduct p join p.shop s where p.status = 'ACTIVE' and p.moderationBlocked = false "
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
