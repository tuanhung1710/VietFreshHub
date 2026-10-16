package VietFreshHub.customer.service;

import VietFreshHub.auth.entity.Address;
import VietFreshHub.auth.entity.User;
import VietFreshHub.auth.repository.AddressRepository;
import VietFreshHub.auth.repository.UserRepository;
import VietFreshHub.customer.dto.CustomerAddressRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class CustomerAddressService {

    private final AddressRepository addressRepository;
    private final UserRepository userRepository;

    public CustomerAddressService(AddressRepository addressRepository, UserRepository userRepository) {
        this.addressRepository = addressRepository;
        this.userRepository = userRepository;
    }

    public List<CustomerAddressRequest> getAddresses(String authenticatedEmail) {
        Long userId = findCurrentUser(authenticatedEmail).getUserId();
        return addressRepository.findAllByUser_UserIdOrderByIsDefaultDescAddressIdAsc(userId)
                .stream()
                .map(this::toRequest)
                .toList();
    }

    public CustomerAddressRequest getAddress(String authenticatedEmail, Long addressId) {
        Long userId = findCurrentUser(authenticatedEmail).getUserId();
        return toRequest(findOwnedAddress(addressId, userId));
    }

    @Transactional
    public void saveAddress(String authenticatedEmail, CustomerAddressRequest request) {
        User user = findCurrentUser(authenticatedEmail);
        Address address = request.getAddressId() == null
                ? new Address()
                : findOwnedAddress(request.getAddressId(), user.getUserId());

        if (request.isDefault()) {
            clearDefaultAddresses(user.getUserId(), address.getAddressId());
        }

        address.setUser(user);
        address.setRecipientName(request.getRecipientName().trim());
        address.setPhone(request.getPhone().trim());
        address.setProvince(request.getProvince().trim());
        address.setDistrict(request.getDistrict().trim());
        address.setWard(request.getWard().trim());
        address.setAddressLine(request.getAddressLine().trim());
        address.setIsDefault(request.isDefault());
        addressRepository.save(address);
    }

    @Transactional
    public void setDefaultAddress(String authenticatedEmail, Long addressId) {
        Long userId = findCurrentUser(authenticatedEmail).getUserId();
        Address address = findOwnedAddress(addressId, userId);
        clearDefaultAddresses(userId, addressId);
        address.setIsDefault(true);
        addressRepository.save(address);
    }

    @Transactional
    public void deleteAddress(String authenticatedEmail, Long addressId) {
        Long userId = findCurrentUser(authenticatedEmail).getUserId();
        addressRepository.delete(findOwnedAddress(addressId, userId));
    }

    private void clearDefaultAddresses(Long userId, Long exceptAddressId) {
        List<Address> defaults = addressRepository.findAllByUser_UserIdAndIsDefaultTrue(userId);
        for (Address defaultAddress : defaults) {
            if (!defaultAddress.getAddressId().equals(exceptAddressId)) {
                defaultAddress.setIsDefault(false);
            }
        }
        addressRepository.flush();
    }

    private Address findOwnedAddress(Long addressId, Long userId) {
        return addressRepository.findByAddressIdAndUser_UserId(addressId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy địa chỉ thuộc tài khoản của bạn."));
    }

    private User findCurrentUser(String authenticatedEmail) {
        return userRepository.findByEmailIgnoreCase(authenticatedEmail)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy tài khoản đang đăng nhập."));
    }

    private CustomerAddressRequest toRequest(Address address) {
        CustomerAddressRequest request = new CustomerAddressRequest();
        request.setAddressId(address.getAddressId());
        request.setRecipientName(address.getRecipientName());
        request.setPhone(address.getPhone());
        request.setProvince(address.getProvince());
        request.setDistrict(address.getDistrict());
        request.setWard(address.getWard());
        request.setAddressLine(address.getAddressLine());
        request.setDefault(Boolean.TRUE.equals(address.getIsDefault()));
        return request;
    }
}
