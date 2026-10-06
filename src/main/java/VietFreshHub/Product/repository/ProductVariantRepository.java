package VietFreshHub.Product.repository;

import VietFreshHub.Product.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    Optional<ProductVariant> findByVariantIdAndStatus(Long variantId, String status);
}
