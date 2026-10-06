package VietFreshHub.Cart.service.impl;

import VietFreshHub.Auth.entity.User;
import VietFreshHub.Auth.repository.UserRepository;
import VietFreshHub.Cart.dto.AddToCartRequest;
import VietFreshHub.Cart.dto.AddToCartResponse;
import VietFreshHub.Cart.entity.Cart;
import VietFreshHub.Cart.entity.CartItem;
import VietFreshHub.Cart.repository.CartItemRepository;
import VietFreshHub.Cart.repository.CartRepository;
import VietFreshHub.Cart.service.CartService;
import VietFreshHub.Inventory.repository.InventoryBatchRepository;
import VietFreshHub.Product.dto.StockCheckResponse;
import VietFreshHub.Product.entity.ProductVariant;
import VietFreshHub.Product.repository.ProductVariantRepository;
import VietFreshHub.exception.OutOfStockException;
import VietFreshHub.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryBatchRepository inventoryBatchRepository;
    private final UserRepository userRepository;

    public static final String MSG21 = "Added to cart.";

    @Override
    @Transactional
    public AddToCartResponse addToCart(Long userId, AddToCartRequest request) {
        log.info("Processing Add-To-Cart for userId={}, variantId={}, quantity={}", 
                userId, request.getVariantId(), request.getQuantity());

        // 1. Validate Variant Existence and Status (Scenario 4 / GBR-19)
        ProductVariant variant = productVariantRepository.findById(request.getVariantId())
                .orElseThrow(() -> new ResourceNotFoundException("Variant is no longer available."));

        if (!"ACTIVE".equalsIgnoreCase(variant.getStatus())) {
            throw new ResourceNotFoundException("Variant is no longer available.");
        }

        // 2. Fetch or Create Active Cart for User
        Cart cart = cartRepository.findByUserUserIdAndStatus(userId, "ACTIVE")
                .orElseGet(() -> createNewActiveCart(userId));

        // 3. Real-time Available Stock Calculation (GBR-12 & GBR-13)
        // available_stock = SUM(quantity_on_hand) - SUM(reserved_quantity)
        Integer availableStock = inventoryBatchRepository.getAvailableStockByVariantId(variant.getVariantId());
        if (availableStock == null) {
            availableStock = 0;
        }

        // 4. Check if Item already exists in Cart
        Optional<CartItem> existingCartItemOpt = cartItemRepository
                .findByCartCartIdAndVariantVariantId(cart.getCartId(), variant.getVariantId());

        int newQuantity = request.getQuantity();
        if (existingCartItemOpt.isPresent()) {
            // Scenario 2: Quantity Accumulation
            newQuantity = existingCartItemOpt.get().getQuantity() + request.getQuantity();
        }

        // 5. Stock Validation (Scenario 3 / GBR-13)
        if (newQuantity > availableStock) {
            log.warn("Insufficient stock for variantId={}: requested total={}, available={}",
                    variant.getVariantId(), newQuantity, availableStock);
            throw new OutOfStockException("Only " + availableStock + " items left in stock", availableStock);
        }

        // 6. Action: Insert or Update Cart Item (Scenario 1 & Scenario 2)
        CartItem savedItem;
        if (existingCartItemOpt.isPresent()) {
            CartItem existingItem = existingCartItemOpt.get();
            existingItem.setQuantity(newQuantity);
            // Real-time price snapshot update if changed (Scenario 2 requirement)
            existingItem.setUnitPriceSnapshot(variant.getPrice());
            savedItem = cartItemRepository.save(existingItem);
            log.info("Updated existing CartItem id={} to quantity={}", savedItem.getCartItemId(), newQuantity);
        } else {
            CartItem newItem = new CartItem();
            newItem.setCart(cart);
            newItem.setVariant(variant);
            newItem.setQuantity(request.getQuantity());
            newItem.setUnitPriceSnapshot(variant.getPrice());
            savedItem = cartItemRepository.save(newItem);
            log.info("Created new CartItem id={} with quantity={}", savedItem.getCartItemId(), request.getQuantity());
        }

        // 7. Response Construction
        BigDecimal itemSubtotal = savedItem.getUnitPriceSnapshot()
                .multiply(BigDecimal.valueOf(savedItem.getQuantity()));

        return AddToCartResponse.builder()
                .cartItemId(savedItem.getCartItemId())
                .cartId(cart.getCartId())
                .variantId(variant.getVariantId())
                .variantName(variant.getVariantName())
                .quantity(savedItem.getQuantity())
                .unitPriceSnapshot(savedItem.getUnitPriceSnapshot())
                .itemSubtotal(itemSubtotal)
                .message(MSG21)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public VietFreshHub.Cart.dto.CartDto getActiveCart(Long userId) {
        Cart cart = cartRepository.findByUserUserIdAndStatus(userId, "ACTIVE")
                .orElseGet(() -> createNewActiveCart(userId));

        java.util.List<VietFreshHub.Cart.dto.CartItemDto> itemDtos = new java.util.ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        int totalItems = 0;

        for (CartItem item : cart.getItems()) {
            BigDecimal subtotal = item.getUnitPriceSnapshot().multiply(BigDecimal.valueOf(item.getQuantity()));
            totalAmount = totalAmount.add(subtotal);
            totalItems += item.getQuantity();

            Integer stock = inventoryBatchRepository.getAvailableStockByVariantId(item.getVariant().getVariantId());

            String imgUrl = (item.getVariant() != null && item.getVariant().getProduct() != null && item.getVariant().getProduct().getImageUrl() != null)
                    ? item.getVariant().getProduct().getImageUrl()
                    : "/images/buoi-da-xanh.jpg";

            itemDtos.add(VietFreshHub.Cart.dto.CartItemDto.builder()
                    .cartItemId(item.getCartItemId())
                    .variantId(item.getVariant().getVariantId())
                    .variantName(item.getVariant().getVariantName())
                    .quantity(item.getQuantity())
                    .unitPriceSnapshot(item.getUnitPriceSnapshot())
                    .itemSubtotal(subtotal)
                    .availableStock(stock != null ? stock : 0)
                    .status(item.getVariant().getStatus())
                    .imageUrl(imgUrl)
                    .build());
        }

        return VietFreshHub.Cart.dto.CartDto.builder()
                .cartId(cart.getCartId())
                .userId(userId)
                .status(cart.getStatus())
                .items(itemDtos)
                .totalItems(totalItems)
                .totalAmount(totalAmount)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public StockCheckResponse getVariantStock(Long variantId) {
        // Scenario 5 Pre-validation
        ProductVariant variant = productVariantRepository.findById(variantId)
                .orElse(null);

        if (variant == null || !"ACTIVE".equalsIgnoreCase(variant.getStatus())) {
            return StockCheckResponse.builder()
                    .variantId(variantId)
                    .availableStock(0)
                    .status(variant != null ? variant.getStatus() : "NOT_FOUND")
                    .purchasable(false)
                    .build();
        }

        Integer stock = inventoryBatchRepository.getAvailableStockByVariantId(variantId);
        int available = stock != null ? stock : 0;
        boolean purchasable = available > 0;

        return StockCheckResponse.builder()
                .variantId(variantId)
                .availableStock(available)
                .status(variant.getStatus())
                .purchasable(purchasable)
                .build();
    }

    private Cart createNewActiveCart(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        Cart cart = new Cart();
        cart.setUser(user);
        cart.setStatus("ACTIVE");
        return cartRepository.save(cart);
    }

    @Override
    @Transactional
    public void updateCartItemQuantity(Long userId, Long variantId, int newQuantity) {
        if (newQuantity <= 0) {
            removeCartItem(userId, variantId);
            return;
        }

        Cart cart = cartRepository.findByUserUserIdAndStatus(userId, "ACTIVE")
                .orElseThrow(() -> new ResourceNotFoundException("Active cart not found"));

        CartItem item = cartItemRepository.findByCartCartIdAndVariantVariantId(cart.getCartId(), variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found"));

        Integer stock = inventoryBatchRepository.getAvailableStockByVariantId(variantId);
        int available = stock != null ? stock : 0;
        if (newQuantity > available) {
            throw new OutOfStockException("Only " + available + " items left in stock", available);
        }

        item.setQuantity(newQuantity);
        cartItemRepository.save(item);
    }

    @Override
    @Transactional
    public void removeCartItem(Long userId, Long variantId) {
        Cart cart = cartRepository.findByUserUserIdAndStatus(userId, "ACTIVE").orElse(null);
        if (cart != null) {
            Optional<CartItem> itemOpt = cartItemRepository.findByCartCartIdAndVariantVariantId(cart.getCartId(), variantId);
            if (itemOpt.isPresent()) {
                CartItem item = itemOpt.get();
                if (cart.getItems() != null) {
                    cart.getItems().remove(item);
                }
                cartItemRepository.delete(item);
                cartItemRepository.flush();
            }
        }
    }
}
