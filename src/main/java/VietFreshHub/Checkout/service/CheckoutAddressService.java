package VietFreshHub.Checkout.service;
import VietFreshHub.Checkout.dto.CheckoutAddressRequest;
public interface CheckoutAddressService {
    Long create(Long customerId,CheckoutAddressRequest request);
}
