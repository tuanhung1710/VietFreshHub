package VietFreshHub.checkout.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record CheckoutDraft(
        String token,
        Long customerId,
        Long cartId,
        String fingerprint,
        Long addressId,
        Map<Long, ShopShippingQuote> shopQuotes,
        BigDecimal shippingFee,
        List<Long> selectedItemIds,
        Long buyNowVariantId,
        Integer buyNowQuantity)
        implements Serializable {
    public CheckoutDraft(
            String token,
            Long customerId,
            Long cartId,
            String fingerprint,
            Long addressId,
            Map<Long, ShopShippingQuote> shopQuotes,
            BigDecimal shippingFee,
            List<Long> selectedItemIds) {
        this(
                token,
                customerId,
                cartId,
                fingerprint,
                addressId,
                shopQuotes,
                shippingFee,
                selectedItemIds,
                null,
                null);
    }

    public boolean buyNow() {
        return buyNowVariantId != null;
    }

    public CheckoutDraft {
        shopQuotes = Map.copyOf(shopQuotes);
        selectedItemIds = List.copyOf(selectedItemIds);
    }
}
