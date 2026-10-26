package VietFreshHub.inventory.service;

import org.springframework.security.core.Authentication;

import java.util.List;

/** Stock is reserved after seller confirmation and sold when the order is ready for delivery. */
public interface StockAllocationService {

    List<Long> reserve(Authentication authentication, Long orderItemId);

    void release(Authentication authentication, Long orderItemId, String reason);

    void sell(Authentication authentication, Long orderItemId);
}
