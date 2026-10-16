package VietFreshHub.checkout.service;
import VietFreshHub.checkout.dto.CheckoutAddressRequest;
public interface CheckoutAddressService {
    Long create(Long customerId,CheckoutAddressRequest request);
}
