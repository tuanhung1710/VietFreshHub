package VietFreshHub.checkout.service;
import VietFreshHub.checkout.dto.CheckoutAddressRequest;
import VietFreshHub.vietmap.dto.VietMapPlaceResponse;
public interface CheckoutAddressService {
    Long create(Long customerId,CheckoutAddressRequest request,VietMapPlaceResponse place);
}
