package VietFreshHub.inventory.service;

import org.springframework.security.core.Authentication;

import java.util.List;

/** Called within Order's checkout, confirmation or cancellation transaction. */
public interface StockAllocationService {

    List<Long> reserve(Authentication authentication, Long orderItemId);

    void release(Authentication authentication, Long orderItemId, String reason);

    void sell(Authentication authentication, Long orderItemId);
}
