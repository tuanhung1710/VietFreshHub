package VietFreshHub.cart.service;

import VietFreshHub.cart.dto.AddToCartResponse;
import VietFreshHub.cart.dto.CartDto;
import VietFreshHub.cart.dto.CartItemDto;
import VietFreshHub.cart.entity.Cart;
import VietFreshHub.cart.entity.CartItem;
import VietFreshHub.catalog.entity.CatalogVariant;

import java.math.BigDecimal;
import java.util.List;

/** Chuyển dữ liệu giỏ hàng sang DTO, không đọc hoặc ghi cơ sở dữ liệu. */
public final class CartViewMapper {
    private CartViewMapper() {
    }

    public static CartDto emptyCart(Long userId) {
        return CartDto.builder()
                .userId(userId)
                .status("ACTIVE")
                .totalItems(0)
                .totalAmount(BigDecimal.ZERO)
                .build();
    }

    public static CartDto toCart(Cart cart, Long userId, List<CartItemDto> items) {
        long totalItems = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        boolean hasUnavailableItems = false;

        for (CartItemDto item : items) {
            totalItems += item.getQuantity();
            // Vẫn hiển thị món không thể mua, nhưng không cộng tiền món đó vào tổng.
            if (item.isPurchasable()) {
                totalAmount = totalAmount.add(item.getItemSubtotal());
            } else {
                hasUnavailableItems = true;
            }
        }

        return CartDto.builder()
                .cartId(cart.getCartId())
                .userId(userId)
                .status(cart.getStatus())
                .items(List.copyOf(items))
                .totalItems(totalItems)
                .totalAmount(totalAmount)
                .hasUnavailableItems(hasUnavailableItems)
                .build();
    }

    public static CartItemDto toCartItem(CartItem item, int availableStock, boolean catalogPurchasable) {
        CatalogVariant variant = item.getVariant();
        BigDecimal currentPrice = variant.getPrice();
        BigDecimal previousPrice = item.getUnitPriceSnapshot();
        BigDecimal itemSubtotal = currentPrice.multiply(BigDecimal.valueOf(item.getQuantity()));
        boolean priceChanged = currentPrice.compareTo(previousPrice) != 0;
        boolean quantityEditable = catalogPurchasable && availableStock > 0;
        boolean purchasable = quantityEditable && item.getQuantity() <= availableStock;
        String availabilityMessage = availabilityMessage(catalogPurchasable, availableStock, item.getQuantity());

        return CartItemDto.builder()
                .cartItemId(item.getCartItemId())
                .variantId(variant.getVariantId())
                .variantName(variant.getVariantName())
                .quantity(item.getQuantity())
                .unitPriceSnapshot(currentPrice)
                .previousPrice(previousPrice)
                .priceChanged(priceChanged)
                .itemSubtotal(itemSubtotal)
                .availableStock(availableStock)
                .status(variant.getStatus())
                .purchasable(purchasable)
                .quantityEditable(quantityEditable)
                .availabilityMessage(availabilityMessage)
                .imageUrl(variant.getDisplayImageUrl())
                .productName(variant.getProduct().getName())
                .shopId(variant.getProduct().getShop().getShopId())
                .shopName(variant.getProduct().getShop().getShopName())
                .build();
    }

    public static AddToCartResponse toAddResponse(Cart cart, CartItem savedItem, CatalogVariant variant) {
        BigDecimal itemSubtotal = variant.getPrice().multiply(BigDecimal.valueOf(savedItem.getQuantity()));
        return AddToCartResponse.builder()
                .cartItemId(savedItem.getCartItemId())
                .cartId(cart.getCartId())
                .variantId(variant.getVariantId())
                .variantName(variant.getVariantName())
                .quantity(savedItem.getQuantity())
                .unitPriceSnapshot(variant.getPrice())
                .itemSubtotal(itemSubtotal)
                .message("Đã thêm sản phẩm vào giỏ hàng.")
                .build();
    }

    private static String availabilityMessage(boolean catalogPurchasable, int availableStock, int quantity) {
        if (!catalogPurchasable) {
            return "Sản phẩm hoặc cửa hàng hiện không nhận đơn.";
        }
        if (availableStock == 0) {
            return "Tạm hết hàng. Bạn có thể giữ lại trong giỏ hoặc xóa sản phẩm.";
        }
        if (quantity > availableStock) {
            return "Chỉ còn " + availableStock + " sản phẩm. Vui lòng giảm số lượng.";
        }
        return null;
    }
}
