package VietFreshHub.catalog.repository;

import VietFreshHub.catalog.entity.CatalogVariant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CatalogVariantRepository extends JpaRepository<CatalogVariant, Long> {}
