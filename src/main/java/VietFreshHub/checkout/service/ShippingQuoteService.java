package VietFreshHub.checkout.service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
@Service
public class ShippingQuoteService {
    private final BigDecimal fee;
    public ShippingQuoteService(@Value("${app.checkout.shipping-fee-per-shop:}") String configuredFee) {
        if(configuredFee==null || configuredFee.isBlank()) { fee=null; return; }
        BigDecimal parsed=new BigDecimal(configuredFee).setScale(2,java.math.RoundingMode.UNNECESSARY);
        if(parsed.signum()<0 || parsed.precision()>18) throw new IllegalArgumentException("Phí giao hàng phải không âm và khớp DECIMAL(18,2).");
        fee=parsed;
    }
    public BigDecimal feePerShop() { return fee; }
}
