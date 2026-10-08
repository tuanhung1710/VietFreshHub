package VietFreshHub.Checkout.dto;
import VietFreshHub.Cart.dto.CartDto;
import java.math.BigDecimal;
import java.util.List;
public record CheckoutPreview(CartDto cart,List<AddressOption> addresses,BigDecimal shippingFeePerShop,
                              BigDecimal shippingFee,BigDecimal grandTotal,boolean codAvailable,String fingerprint) {
    public record AddressOption(Long addressId,String recipientName,String phone,String addressText,boolean defaultAddress) {}
    public boolean canConfirm() { return !cart.getItems().isEmpty() && !cart.isHasUnavailableItems()
        && !addresses.isEmpty() && shippingFeePerShop!=null && codAvailable; }
}
