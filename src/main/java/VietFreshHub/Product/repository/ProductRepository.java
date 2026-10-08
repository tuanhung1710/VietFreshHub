package VietFreshHub.Product.repository;

import VietFreshHub.Product.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    @org.springframework.data.jpa.repository.Query(value = "select p from Product p join fetch p.shop s "
            + "where p.status = 'ACTIVE' and p.approvalStatus = 'APPROVED' and s.status = 'ACTIVE' "
            + "and exists (select a.applicationId from SellerApplication a where a.applicationId = s.applicationId "
            + "and a.status = VietFreshHub.Auth.entity.SellerApplicationStatus.APPROVED) "
            + "and (:keyword = '' or locate(lower(:keyword), lower(p.name)) > 0)",
            countQuery = "select count(p) from Product p join p.shop s where p.status = 'ACTIVE' and p.approvalStatus = 'APPROVED' "
            + "and s.status = 'ACTIVE' and exists (select a.applicationId from SellerApplication a where a.applicationId = s.applicationId "
            + "and a.status = VietFreshHub.Auth.entity.SellerApplicationStatus.APPROVED) "
            + "and (:keyword = '' or locate(lower(:keyword), lower(p.name)) > 0)")
    org.springframework.data.domain.Page<Product> findVisibleCatalog(@org.springframework.data.repository.query.Param("keyword") String keyword,
                                                                   org.springframework.data.domain.Pageable pageable);

    default org.springframework.data.domain.Page<Product> findVisibleCatalog(org.springframework.data.domain.Pageable pageable) {
        return findVisibleCatalog("", pageable);
    }

    java.util.List<Product> findAllBySlug(String slug);

    Optional<Product> findByName(String name);
    Optional<Product> findBySlug(String slug);
}
