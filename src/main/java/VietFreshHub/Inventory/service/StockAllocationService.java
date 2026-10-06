package VietFreshHub.Inventory.service;
import org.springframework.security.core.Authentication;
import java.util.List;
/** To be invoked inside Order's checkout/confirmation/cancellation transaction. */
public interface StockAllocationService {
    List<Long> reserve(Authentication authentication,Long orderItemId);
    void release(Authentication authentication,Long orderItemId,String reason);
    void sell(Authentication authentication,Long orderItemId);
}
