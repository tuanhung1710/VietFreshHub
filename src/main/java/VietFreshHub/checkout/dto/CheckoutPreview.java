package VietFreshHub.checkout.dto;
import VietFreshHub.cart.dto.CartDto;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
public record CheckoutPreview(CartDto cart,List<AddressOption> addresses,Long addressId,
                              Map<Long,ShopShippingQuote> shopQuotes,BigDecimal shippingFee,BigDecimal grandTotal,
                              boolean codAvailable,boolean vnpayAvailable,String fingerprint,String shippingError,boolean deliveryOutOfRange) {
    public CheckoutPreview { shopQuotes=Map.copyOf(shopQuotes); }
    public record AddressOption(Long addressId,String recipientName,String phone,String addressText,boolean defaultAddress,
                                boolean locationComplete) {}
    public boolean canConfirm() { return !cart.getItems().isEmpty() && !cart.isHasUnavailableItems()
        && addressId!=null && addresses.stream().anyMatch(a -> addressId.equals(a.addressId()) && a.locationComplete())
        && !deliveryOutOfRange && shippingError==null && shippingFee!=null && grandTotal!=null && (codAvailable || vnpayAvailable)
        && shopQuotes.keySet().equals(cart.getItems().stream().map(VietFreshHub.cart.dto.CartItemDto::getShopId)
                .collect(java.util.stream.Collectors.toSet())); }
}
