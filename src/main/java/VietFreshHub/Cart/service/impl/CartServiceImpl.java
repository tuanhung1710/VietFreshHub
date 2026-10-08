package VietFreshHub.Cart.service.impl;

import VietFreshHub.Auth.entity.User;
import VietFreshHub.Auth.repository.UserRepository;
import VietFreshHub.Cart.dto.*;
import VietFreshHub.Cart.entity.*;
import VietFreshHub.Cart.repository.*;
import VietFreshHub.Cart.service.CartService;
import VietFreshHub.Inventory.repository.InventoryBatchRepository;
import VietFreshHub.Product.dto.StockCheckResponse;
import VietFreshHub.Product.entity.ProductVariant;
import VietFreshHub.Product.repository.ProductVariantRepository;
import VietFreshHub.Product.service.CatalogService;
import VietFreshHub.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryBatchRepository inventoryBatchRepository;
    private final UserRepository userRepository;
    private final CatalogService catalogService;

    @Override
    @Transactional
    public AddToCartResponse addToCart(Long userId, AddToCartRequest request) {
        if (request == null || request.getVariantId() == null || request.getVariantId() <= 0
                || request.getQuantity() == null || request.getQuantity() < 1) {
            throw new IllegalArgumentException("Vui lòng chọn sản phẩm và nhập số lượng từ 1 trở lên.");
        }
        User user = lockCustomer(userId);
        ProductVariant variant = requirePurchasable(request.getVariantId());
        Cart cart = cartRepository.findByUserUserIdAndStatus(userId, "ACTIVE").orElseGet(() -> {
            Cart created = new Cart();
            created.setUser(user);
            return cartRepository.save(created);
        });
        CartItem item = cartItemRepository.findByCartCartIdAndVariantVariantId(cart.getCartId(), variant.getVariantId())
                .orElseGet(() -> {
                    CartItem created = new CartItem();
                    created.setCart(cart);
                    created.setVariant(variant);
                    created.setQuantity(0);
                    return created;
                });
        long quantity = (long) item.getQuantity() + request.getQuantity();
        int stock = availableStock(variant.getVariantId());
        if (quantity > stock) throw insufficientStock(stock);
        item.setQuantity((int) quantity);
        item.setUnitPriceSnapshot(variant.getPrice());
        CartItem saved = cartItemRepository.save(item);
        return AddToCartResponse.builder().cartItemId(saved.getCartItemId()).cartId(cart.getCartId())
                .variantId(variant.getVariantId()).variantName(variant.getVariantName()).quantity(saved.getQuantity())
                .unitPriceSnapshot(variant.getPrice()).itemSubtotal(variant.getPrice().multiply(BigDecimal.valueOf(quantity)))
                .message("Đã thêm sản phẩm vào giỏ hàng.").build();
    }

    @Override
    @Transactional(readOnly = true)
    public CartDto getActiveCart(Long userId) {
        Cart cart = cartRepository.findByUserUserIdAndStatus(userId, "ACTIVE").orElse(null);
        if (cart == null) return CartDto.builder().userId(userId).status("ACTIVE").totalItems(0).totalAmount(BigDecimal.ZERO).build();
        var stocks = inventoryBatchRepository.getAvailableStocks(cart.getItems().stream()
                .map(i -> i.getVariant().getVariantId()).distinct().toList());
        List<CartItemDto> items = cart.getItems().stream().map(i -> toItem(i, stocks.getOrDefault(i.getVariant().getVariantId(), 0)))
                .sorted(Comparator.comparing(CartItemDto::getShopId).thenComparing(CartItemDto::getCartItemId)).toList();
        return CartDto.builder().cartId(cart.getCartId()).userId(userId).status(cart.getStatus()).items(items)
                .totalItems(items.stream().mapToLong(CartItemDto::getQuantity).sum())
                .totalAmount(items.stream().filter(CartItemDto::isPurchasable).map(CartItemDto::getItemSubtotal)
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .hasUnavailableItems(items.stream().anyMatch(i -> !i.isPurchasable())).build();
    }

    private CartItemDto toItem(CartItem item, int stock) {
        ProductVariant variant = item.getVariant();
        boolean eligible = catalogService.isPurchasable(variant);
        boolean purchasable = eligible && stock > 0 && item.getQuantity() <= stock;
        String message = !eligible ? "Sản phẩm hoặc cửa hàng hiện không nhận đơn."
                : stock == 0 ? "Tạm hết hàng. Bạn có thể giữ lại trong giỏ hoặc xóa sản phẩm."
                : item.getQuantity() > stock ? "Chỉ còn " + stock + " sản phẩm. Vui lòng giảm số lượng." : null;
        return CartItemDto.builder().cartItemId(item.getCartItemId()).variantId(variant.getVariantId())
                .variantName(variant.getVariantName()).quantity(item.getQuantity()).unitPriceSnapshot(variant.getPrice())
                .previousPrice(item.getUnitPriceSnapshot()).priceChanged(variant.getPrice().compareTo(item.getUnitPriceSnapshot()) != 0)
                .itemSubtotal(variant.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .availableStock(stock).status(variant.getStatus()).purchasable(purchasable).quantityEditable(eligible && stock > 0).availabilityMessage(message)
                .imageUrl(variant.getProduct().getImageUrl()).productName(variant.getProduct().getName())
                .shopId(variant.getProduct().getShop().getShopId()).shopName(variant.getProduct().getShop().getShopName()).build();
    }

    @Override
    @Transactional(readOnly = true)
    public StockCheckResponse getVariantStock(Long variantId) {
        ProductVariant variant = productVariantRepository.findById(variantId).orElse(null);
        boolean eligible = catalogService.isPurchasable(variant);
        int stock = eligible ? availableStock(variantId) : 0;
        return StockCheckResponse.builder().variantId(variantId).availableStock(stock)
                .status(variant == null ? "NOT_FOUND" : variant.getStatus()).purchasable(eligible && stock > 0).build();
    }

    @Override
    @Transactional
    public void updateCartItemQuantity(Long userId, Long variantId, int quantity) {
        if (quantity < 0) throw new IllegalArgumentException("Số lượng không được âm.");
        lockCustomer(userId);
        Cart cart = cartRepository.findByUserUserIdAndStatus(userId, "ACTIVE")
                .orElseThrow(() -> new ResourceNotFoundException("Giỏ hàng đang trống."));
        CartItem item = cartItemRepository.findByCartCartIdAndVariantVariantId(cart.getCartId(), variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Sản phẩm không còn trong giỏ hàng."));
        if (quantity == 0) {
            cart.getItems().remove(item);
            cartItemRepository.delete(item);
            return;
        }
        ProductVariant variant = requirePurchasable(variantId);
        int stock = availableStock(variantId);
        if (quantity > stock) throw insufficientStock(stock);
        item.setQuantity(quantity);
        item.setUnitPriceSnapshot(variant.getPrice());
        cartItemRepository.save(item);
    }

    @Override
    @Transactional
    public void removeCartItem(Long userId, Long variantId) {
        lockCustomer(userId);
        cartRepository.findByUserUserIdAndStatus(userId, "ACTIVE").ifPresent(cart ->
                cartItemRepository.findByCartCartIdAndVariantVariantId(cart.getCartId(), variantId).ifPresent(item -> {
                    cart.getItems().remove(item);
                    cartItemRepository.delete(item);
                }));
    }

    @Override
    @Transactional
    public void clearCart(Long userId) {
        lockCustomer(userId);
        cartRepository.findByUserUserIdAndStatus(userId, "ACTIVE").ifPresent(cart -> cart.getItems().clear());
    }

    private User lockCustomer(Long userId) {
        // Serialize mutations on the user row, including creation of the first active cart.
        User user = userRepository.findForCartUpdate(userId)
                .orElseThrow(() -> new AccessDeniedException("Tài khoản không hợp lệ."));
        if (!"ACTIVE".equals(user.getStatus())) throw new AccessDeniedException("Tài khoản không còn được phép mua hàng.");
        return user;
    }

    private ProductVariant requirePurchasable(Long variantId) {
        ProductVariant variant = productVariantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Sản phẩm không còn được bán."));
        if (!catalogService.isPurchasable(variant)) throw new ResourceNotFoundException("Sản phẩm hoặc cửa hàng hiện không nhận đơn.");
        return variant;
    }
    private int availableStock(Long variantId) {
        Integer stock = inventoryBatchRepository.getAvailableStockByVariantId(variantId);
        return stock == null ? 0 : stock;
    }
    private OutOfStockException insufficientStock(int stock) {
        return new OutOfStockException("Chỉ còn " + stock + " sản phẩm có thể mua. Vui lòng điều chỉnh số lượng.", stock);
    }
}
