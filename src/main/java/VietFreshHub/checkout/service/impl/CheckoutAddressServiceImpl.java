package VietFreshHub.checkout.service.impl;
import VietFreshHub.checkout.service.CheckoutAddressService;
import VietFreshHub.checkout.dto.CheckoutAddressRequest;
import VietFreshHub.customer.service.CustomerAddressService;
import VietFreshHub.exception.CheckoutException;
import VietFreshHub.vietmap.dto.VietMapPlaceResponse;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
@Service @RequiredArgsConstructor
public class CheckoutAddressServiceImpl implements CheckoutAddressService {
    private final CustomerAddressService customerAddresses;
    private final Validator validator;
    @Override
    public Long create(Long customerId,CheckoutAddressRequest form,VietMapPlaceResponse place) {
        var errors=validator.validate(form);
        if(!errors.isEmpty()) throw new CheckoutException(errors.iterator().next().getMessage());
        try {
            customerAddresses.validateDeliveryLocation(place);
            return customerAddresses.createCheckoutAddress(customerId,form.getRecipientName(),form.getPhone(),place);
        } catch(IllegalArgumentException ex) {
            throw new CheckoutException(ex.getMessage());
        }
    }
}
