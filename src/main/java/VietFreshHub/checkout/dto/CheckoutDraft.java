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
        List<Long> selectedItemIds
) implements Serializable {
    public CheckoutDraft {
        shopQuotes = Map.copyOf(shopQuotes);
        selectedItemIds = List.copyOf(selectedItemIds);
    }
}
