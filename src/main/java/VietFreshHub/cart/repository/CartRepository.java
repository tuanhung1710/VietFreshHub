package VietFreshHub.cart.repository;

import VietFreshHub.cart.entity.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CartRepository extends JpaRepository<Cart, Long> {

    @EntityGraph(attributePaths = {"items.variant.product.shop"})
    Optional<Cart> findByUserUserIdAndStatus(Long userId, String status);
}
