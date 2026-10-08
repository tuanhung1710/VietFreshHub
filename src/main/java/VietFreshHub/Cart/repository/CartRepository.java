package VietFreshHub.Cart.repository;

import VietFreshHub.Cart.entity.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CartRepository extends JpaRepository<Cart, Long> {

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"items.variant.product.shop"})
    Optional<Cart> findByUserUserIdAndStatus(Long userId, String status);
}
