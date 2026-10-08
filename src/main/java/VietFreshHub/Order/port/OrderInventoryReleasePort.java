package VietFreshHub.Order.port;
import java.util.List;
public interface OrderInventoryReleasePort {
    record Line(Long orderItemId,Long variantId,int quantity) {}
    /** Release exact outstanding allocations in the caller's transaction; never infer batches from current stock. */
    void release(List<Line> lines,Long actorId);
}
