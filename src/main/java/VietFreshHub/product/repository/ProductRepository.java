package VietFreshHub.product.repository;

import VietFreshHub.product.entity.Product;
import VietFreshHub.product.entity.Category;
import VietFreshHub.product.service.CatalogCategoryPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    @org.springframework.data.jpa.repository.Query(value = "select p from Product p join fetch p.shop s "
            + "where p.status = 'ACTIVE' and p.approvalStatus = 'APPROVED' and s.status = 'ACTIVE' "
            + "and exists (select c.categoryId from p.categories c where c.categoryId in :categoryIds) "
            + "and exists (select v.variantId from ProductVariant v where v.product = p and v.status in ('ACTIVE','OUT_OF_STOCK')) "
            + "and (:keyword = '' or locate(lower(:keyword), lower(p.name)) > 0)",
            countQuery = "select count(p) from Product p join p.shop s where p.status = 'ACTIVE' and p.approvalStatus = 'APPROVED' "
            + "and s.status = 'ACTIVE' and exists (select c.categoryId from p.categories c where c.categoryId in :categoryIds) "
            + "and exists (select v.variantId from ProductVariant v where v.product = p and v.status in ('ACTIVE','OUT_OF_STOCK')) "
            + "and (:keyword = '' or locate(lower(:keyword), lower(p.name)) > 0)")
    org.springframework.data.domain.Page<Product> findVisibleCatalogForCategories(@org.springframework.data.repository.query.Param("keyword") String keyword,
                                                                   @org.springframework.data.repository.query.Param("categoryIds") java.util.Set<Long> categoryIds,
                                                                   org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query("select c from Category c")
    java.util.List<Category> findCatalogCategories();

    default org.springframework.data.domain.Page<Product> findVisibleCatalog(String keyword, org.springframework.data.domain.Pageable pageable) {
        var ids = CatalogCategoryPolicy.activeIds(findCatalogCategories());
        return ids.isEmpty() ? org.springframework.data.domain.Page.empty(pageable) : findVisibleCatalogForCategories(keyword, ids, pageable);
    }

    default org.springframework.data.domain.Page<Product> findVisibleCatalog(org.springframework.data.domain.Pageable pageable) {
        return findVisibleCatalog("", pageable);
    }

    java.util.List<Product> findAllBySlug(String slug);

    Optional<Product> findByName(String name);
    Optional<Product> findBySlug(String slug);
}
