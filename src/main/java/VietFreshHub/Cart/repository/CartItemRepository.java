package VietFreshHub.Cart.repository;

import VietFreshHub.Cart.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    Optional<CartItem> findByCartCartIdAndVariantVariantId(Long cartId, Long variantId);
}
