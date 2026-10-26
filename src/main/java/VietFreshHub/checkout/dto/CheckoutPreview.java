package VietFreshHub.checkout.dto;

import VietFreshHub.cart.dto.CartDto;
import VietFreshHub.cart.dto.CartItemDto;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record CheckoutPreview(
        CartDto cart,
        List<AddressOption> addresses,
        Long addressId,
        Map<Long, ShopShippingQuote> shopQuotes,
        BigDecimal shippingFee,
        BigDecimal grandTotal,
        boolean codAvailable,
        String fingerprint,
        String shippingError,
        boolean deliveryOutOfRange
) {
    public CheckoutPreview {
        shopQuotes = Map.copyOf(shopQuotes);
    }

    public record AddressOption(
            Long addressId,
            String recipientName,
            String phone,
            String addressText,
            boolean defaultAddress,
            boolean locationComplete
    ) {
    }

    public boolean canConfirm() {
        if (cart.getItems().isEmpty() || cart.isHasUnavailableItems()) {
            return false;
        }
        if (!hasValidDeliveryAddress()) {
            return false;
        }
        if (deliveryOutOfRange || shippingError != null || shippingFee == null || grandTotal == null || !codAvailable) {
            return false;
        }

        Set<Long> shopIds = new HashSet<>();
        for (CartItemDto item : cart.getItems()) {
            shopIds.add(item.getShopId());
        }
        return shopQuotes.keySet().equals(shopIds);
    }

    private boolean hasValidDeliveryAddress() {
        if (addressId == null) {
            return false;
        }
        for (AddressOption address : addresses) {
            if (addressId.equals(address.addressId()) && address.locationComplete()) {
                return true;
            }
        }
        return false;
    }
}
