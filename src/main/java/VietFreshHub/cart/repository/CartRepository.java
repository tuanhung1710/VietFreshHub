package VietFreshHub.cart.repository;

import VietFreshHub.cart.entity.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CartRepository extends JpaRepository<Cart, Long> {

    @org.springframework.data.jpa.repository.Query("select coalesce(sum(cast(i.quantity as long)),0) from CartItem i "
            + "where i.cart.user.userId=:customerId and i.cart.status='ACTIVE'")
    long countActiveUnits(@org.springframework.data.repository.query.Param("customerId") Long customerId);

    @EntityGraph(attributePaths = {"items.variant.product.shop"})
    Optional<Cart> findByUserUserIdAndStatus(Long userId, String status);
}
