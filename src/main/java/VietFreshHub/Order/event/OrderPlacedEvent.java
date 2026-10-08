package VietFreshHub.Order.event;
import java.util.List;
/** Consumers should handle this via @TransactionalEventListener(AFTER_COMMIT). */
public record OrderPlacedEvent(Long orderId,Long customerId,List<Long> shopOrderIds) {}
