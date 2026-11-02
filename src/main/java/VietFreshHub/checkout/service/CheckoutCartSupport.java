package VietFreshHub.checkout.service;

import VietFreshHub.cart.dto.CartDto;
import VietFreshHub.cart.dto.CartItemDto;
import VietFreshHub.cart.entity.CartItem;
import VietFreshHub.exception.CheckoutException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Các phép tính và thao tác trên giỏ, không đọc hoặc ghi cơ sở dữ liệu. */
public final class CheckoutCartSupport {
    private CheckoutCartSupport() {
    }

    public static Map<Long, List<CartItem>> groupItemsByShop(List<CartItem> selectedItems) {
        Map<Long, List<CartItem>> itemsByShopId = new TreeMap<>();
        for (CartItem item : selectedItems) {
            Long shopId = item.getVariant().getProduct().getShop().getShopId();
            // Các món cùng shop sẽ được tạo chung một ShopOrder.
            List<CartItem> shopItems = itemsByShopId.get(shopId);
            if (shopItems == null) {
                shopItems = new ArrayList<>();
                itemsByShopId.put(shopId, shopItems);
            }
            shopItems.add(item);
        }
        return itemsByShopId;
    }

    public static BigDecimal calculateSubtotal(List<CartItem> items) {
        BigDecimal subtotal = BigDecimal.ZERO;
        for (CartItem item : items) {
            subtotal = subtotal.add(calculateLineTotal(item));
        }
        return subtotal;
    }

    public static BigDecimal calculateLineTotal(CartItem item) {
        return item.getVariant().getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
    }

    public static CartDto selectCartItems(CartDto cart, List<Long> itemIds) {
        if (itemIds == null) {
            return cart;
        }
        if (itemIds.isEmpty()) {
            throw new CheckoutException("Vui lòng chọn ít nhất một sản phẩm hợp lệ.");
        }
        for (Long itemId : itemIds) {
            if (itemId == null || itemId <= 0) {
                throw new CheckoutException("Vui lòng chọn ít nhất một sản phẩm hợp lệ.");
            }
        }
        Set<Long> selectedIds = new HashSet<>(itemIds);
        List<CartItemDto> selected = new ArrayList<>();
        long totalItems = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        boolean hasUnavailableItems = false;

        for (CartItemDto item : cart.getItems()) {
            if (selectedIds.contains(item.getCartItemId())) {
                selected.add(item);
                totalItems += item.getQuantity();
                totalAmount = totalAmount.add(item.getItemSubtotal());
                if (!item.isPurchasable()) {
                    hasUnavailableItems = true;
                }
            }
        }
        if (selected.size() != selectedIds.size()) {
            throw new CheckoutException("Sản phẩm đã chọn không thuộc giỏ hàng hiện tại của bạn.");
        }
        return CartDto.builder()
                .cartId(cart.getCartId())
                .userId(cart.getUserId())
                .status(cart.getStatus())
                .items(selected)
                .totalItems(totalItems)
                .totalAmount(totalAmount)
                .hasUnavailableItems(hasUnavailableItems)
                .build();
    }

    public static BigDecimal normalizeMoney(BigDecimal amount) {
        BigDecimal normalizedAmount = amount.setScale(2, RoundingMode.UNNECESSARY);
        if (normalizedAmount.signum() < 0 || normalizedAmount.precision() > 18) {
            throw new CheckoutException("Giá trị đơn vượt giới hạn cho phép.");
        }
        return normalizedAmount;
    }

    public static String createCartFingerprint(CartDto cart) {
        // Sắp xếp cố định để fingerprint không đổi khi thứ tự hiển thị trong giỏ thay đổi.
        List<CartItemDto> sortedItems = new ArrayList<>(cart.getItems());
        sortedItems.sort(Comparator.comparing(CartItemDto::getCartItemId));
        StringBuilder cartSignature = new StringBuilder(String.valueOf(cart.getCartId()));
        for (CartItemDto item : sortedItems) {
            cartSignature.append('|')
                    .append(item.getCartItemId()).append(':')
                    .append(item.getVariantId()).append(':')
                    .append(item.getQuantity()).append(':')
                    .append(item.getUnitPriceSnapshot().stripTrailingZeros().toPlainString()).append(':')
                    .append(item.getShopId());
        }
        try {
            byte[] signatureBytes = cartSignature.toString().getBytes(StandardCharsets.UTF_8);
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(signatureBytes);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        }
    }
}
