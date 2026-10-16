package VietFreshHub.catalog.repository;

import VietFreshHub.catalog.entity.CatalogVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CatalogVariantRepository extends JpaRepository<CatalogVariant, Long> {

    Optional<CatalogVariant> findByVariantIdAndStatus(Long variantId, String status);
}
