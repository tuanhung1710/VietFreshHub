package VietFreshHub.Checkout.port;
import java.util.List;
public interface InventoryReservationPort {
    record Reservation(Long orderItemId,Long variantId,int quantity) {}
    /** Requires the caller's transaction. Reserve all lines or throw and roll back everything. */
    void reserve(List<Reservation> lines,Long customerId);
}
