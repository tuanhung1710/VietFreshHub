package VietFreshHub.cart.service.impl;

import VietFreshHub.auth.entity.User;
import VietFreshHub.auth.repository.UserRepository;
import VietFreshHub.cart.dto.AddToCartRequest;
import VietFreshHub.cart.dto.AddToCartResponse;
import VietFreshHub.cart.dto.CartDto;
import VietFreshHub.cart.dto.CartItemDto;
import VietFreshHub.cart.entity.Cart;
import VietFreshHub.cart.entity.CartItem;
import VietFreshHub.cart.repository.CartItemRepository;
import VietFreshHub.cart.repository.CartRepository;
import VietFreshHub.cart.service.CartService;
import VietFreshHub.cart.service.CartViewMapper;
import VietFreshHub.catalog.dto.StockCheckResponse;
import VietFreshHub.catalog.entity.CatalogVariant;
import VietFreshHub.catalog.repository.CatalogVariantRepository;
import VietFreshHub.catalog.service.CatalogService;
import VietFreshHub.checkout.repository.CheckoutInventoryBatchRepository;
import VietFreshHub.exception.OutOfStockException;
import VietFreshHub.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Xử lý giỏ của từng khách. Thao tác giỏ chỉ kiểm tra tồn kho, chưa giữ hàng. */
@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final CatalogVariantRepository productVariantRepository;
    private final CheckoutInventoryBatchRepository inventoryBatchRepository;
    private final UserRepository userRepository;
    private final CatalogService catalogService;

    @Override
    @Transactional
    public AddToCartResponse addToCart(Long userId, AddToCartRequest request) {
        validateAddRequest(request);
        User user = lockCustomer(userId);
        CatalogVariant variant = requirePurchasable(request.getVariantId());
        Cart cart = findOrCreateCart(userId, user);
        CartItem item = findOrCreateItem(cart, variant);

        // Dùng long để phép cộng không bị tràn số trước khi kiểm tra tồn kho.
        long combinedQuantity = (long) item.getQuantity() + request.getQuantity();
        int stock = availableStock(variant.getVariantId());
        if (combinedQuantity > stock) {
            throw insufficientStock(variant, combinedQuantity, stock);
        }

        item.setQuantity((int) combinedQuantity);
        item.setUnitPriceSnapshot(variant.getPrice());
        CartItem savedItem = cartItemRepository.save(item);
        return CartViewMapper.toAddResponse(cart, savedItem, variant);
    }

    @Override
    @Transactional(readOnly = true)
    public long countActiveUnits(Long userId) {
        return cartRepository.countActiveUnits(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public CartDto getActiveCart(Long userId) {
        Cart cart = cartRepository.findByUserUserIdAndStatus(userId, "ACTIVE").orElse(null);
        if (cart == null) {
            return CartViewMapper.emptyCart(userId);
        }

        List<Long> variantIds = collectVariantIds(cart);
        Map<Long, Integer> stocks = inventoryBatchRepository.getAvailableStocks(variantIds);
        List<CatalogVariant> cartVariants = new ArrayList<>();
        for (CartItem item : cart.getItems()) {
            cartVariants.add(item.getVariant());
        }
        Map<Long, Boolean> purchasableVariants = catalogService.purchasableVariants(cartVariants);
        List<CartItemDto> items = new ArrayList<>();
        for (CartItem item : cart.getItems()) {
            CatalogVariant variant = item.getVariant();
            int stock = stocks.getOrDefault(variant.getVariantId(), 0);
            boolean catalogPurchasable = purchasableVariants.getOrDefault(variant.getVariantId(), false);
            CartItemDto itemDto = CartViewMapper.toCartItem(item, stock, catalogPurchasable);
            items.add(itemDto);
        }

        items.sort(Comparator.comparing(CartItemDto::getShopId)
                .thenComparing(CartItemDto::getCartItemId));
        return CartViewMapper.toCart(cart, userId, items);
    }

    @Override
    @Transactional(readOnly = true)
    public StockCheckResponse getVariantStock(Long variantId) {
        CatalogVariant variant = productVariantRepository.findById(variantId).orElse(null);
        boolean catalogPurchasable = catalogService.isPurchasable(variant);
        int stock = 0;
        if (catalogPurchasable) {
            stock = availableStock(variantId);
        }

        String status = "NOT_FOUND";
        if (variant != null) {
            status = variant.getStatus();
        }
        return StockCheckResponse.builder()
                .variantId(variantId)
                .availableStock(stock)
                .status(status)
                .purchasable(catalogPurchasable && stock > 0)
                .build();
    }

    @Override
    @Transactional
    public void updateCartItemQuantity(Long userId, Long variantId, int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("Số lượng không được âm.");
        }
        lockCustomer(userId);
        Cart cart = cartRepository.findByUserUserIdAndStatus(userId, "ACTIVE")
                .orElseThrow(() -> new ResourceNotFoundException("Giỏ hàng đang trống."));
        CartItem item = cartItemRepository.findByCartCartIdAndVariantVariantId(cart.getCartId(), variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Sản phẩm không còn trong giỏ hàng."));

        // Cho phép xóa cả sản phẩm đã hết hàng hoặc ngừng bán.
        if (quantity == 0) {
            deleteItem(cart, item);
            return;
        }

        CatalogVariant variant = requirePurchasable(variantId);
        int stock = availableStock(variantId);
        if (quantity > stock) {
            throw insufficientStock(variant, quantity, stock);
        }
        item.setQuantity(quantity);
        item.setUnitPriceSnapshot(variant.getPrice());
        cartItemRepository.save(item);
    }

    @Override
    @Transactional
    public void removeCartItem(Long userId, Long variantId) {
        lockCustomer(userId);
        Cart cart = cartRepository.findByUserUserIdAndStatus(userId, "ACTIVE").orElse(null);
        if (cart == null) {
            return;
        }
        CartItem item = cartItemRepository.findByCartCartIdAndVariantVariantId(cart.getCartId(), variantId)
                .orElse(null);
        if (item != null) {
            deleteItem(cart, item);
        }
    }

    @Override
    @Transactional
    public void clearCart(Long userId) {
        lockCustomer(userId);
        Cart cart = cartRepository.findByUserUserIdAndStatus(userId, "ACTIVE").orElse(null);
        if (cart != null) {
            cart.getItems().clear();
        }
    }

    private void validateAddRequest(AddToCartRequest request) {
        if (request == null || request.getVariantId() == null || request.getVariantId() <= 0
                || request.getQuantity() == null || request.getQuantity() < 1) {
            throw new IllegalArgumentException("Vui lòng chọn sản phẩm và nhập số lượng từ 1 trở lên.");
        }
    }

    private Cart findOrCreateCart(Long userId, User user) {
        Cart cart = cartRepository.findByUserUserIdAndStatus(userId, "ACTIVE").orElse(null);
        if (cart != null) {
            return cart;
        }
        Cart newCart = new Cart();
        newCart.setUser(user);
        return cartRepository.save(newCart);
    }

    private CartItem findOrCreateItem(Cart cart, CatalogVariant variant) {
        CartItem item = cartItemRepository.findByCartCartIdAndVariantVariantId(cart.getCartId(), variant.getVariantId())
                .orElse(null);
        if (item != null) {
            return item;
        }
        CartItem newItem = new CartItem();
        newItem.setCart(cart);
        newItem.setVariant(variant);
        newItem.setQuantity(0);
        return newItem;
    }

    private List<Long> collectVariantIds(Cart cart) {
        // Bỏ ID trùng nhưng giữ thứ tự xuất hiện để chỉ đọc tồn kho một lần cho mỗi biến thể.
        Set<Long> variantIds = new LinkedHashSet<>();
        for (CartItem item : cart.getItems()) {
            variantIds.add(item.getVariant().getVariantId());
        }
        return List.copyOf(variantIds);
    }

    private void deleteItem(Cart cart, CartItem item) {
        cart.getItems().remove(item);
        cartItemRepository.delete(item);
    }

    private User lockCustomer(Long userId) {
        // Khóa tài khoản trước khi sửa giỏ, kể cả lúc tạo giỏ đầu tiên.
        User user = userRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new AccessDeniedException("Tài khoản không hợp lệ."));
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new AccessDeniedException("Tài khoản không còn được phép mua hàng.");
        }
        return user;
    }

    private CatalogVariant requirePurchasable(Long variantId) {
        CatalogVariant variant = productVariantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Sản phẩm không còn được bán."));
        if (!catalogService.isPurchasable(variant)) {
            throw new ResourceNotFoundException("Sản phẩm hoặc cửa hàng hiện không nhận đơn.");
        }
        return variant;
    }

    private int availableStock(Long variantId) {
        Integer stock = inventoryBatchRepository.getAvailableStockByVariantId(variantId);
        if (stock == null) {
            return 0;
        }
        return stock;
    }

    private OutOfStockException insufficientStock(CatalogVariant variant, long requestedQuantity, int stock) {
        String message = variant.getProduct().getName() + " — " + variant.getVariantName()
                + ": tổng số lượng trong giỏ cần " + requestedQuantity + ", hiện chỉ còn " + stock
                + " sản phẩm có thể mua. Vui lòng điều chỉnh số lượng.";
        return new OutOfStockException(message, stock);
    }
}
