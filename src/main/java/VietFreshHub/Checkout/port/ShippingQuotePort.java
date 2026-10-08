package VietFreshHub.Checkout.port;
import java.math.BigDecimal;
public interface ShippingQuotePort {
    /** Null indicates the deployment has no approved shipping policy configured. */
    BigDecimal feePerShop();
}
