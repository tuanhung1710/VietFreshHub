package VietFreshHub.Checkout.dto;
import java.io.Serializable;
import java.math.BigDecimal;
public record CheckoutDraft(String token,Long customerId,Long cartId,String fingerprint,BigDecimal shippingFeePerShop,
                            java.util.List<Long> selectedItemIds) implements Serializable {
    public CheckoutDraft { selectedItemIds=java.util.List.copyOf(selectedItemIds); }
}
