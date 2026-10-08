package VietFreshHub.Checkout.adapter;
import VietFreshHub.Checkout.port.ShippingQuotePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
@Component
public class FixedShippingQuoteAdapter implements ShippingQuotePort {
    private final BigDecimal fee;
    public FixedShippingQuoteAdapter(@Value("${app.checkout.shipping-fee-per-shop:}") String configuredFee) {
        if(configuredFee==null || configuredFee.isBlank()) { fee=null; return; }
        BigDecimal parsed=new BigDecimal(configuredFee).setScale(2,java.math.RoundingMode.UNNECESSARY);
        if(parsed.signum()<0 || parsed.precision()>18) throw new IllegalArgumentException("Phí giao hàng phải không âm và khớp DECIMAL(18,2).");
        fee=parsed;
    }
    @Override public BigDecimal feePerShop() { return fee; }
}
