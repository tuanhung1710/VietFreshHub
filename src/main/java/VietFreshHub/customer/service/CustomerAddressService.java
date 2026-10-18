package VietFreshHub.customer.service;

import VietFreshHub.auth.entity.Address;
import VietFreshHub.auth.entity.User;
import VietFreshHub.auth.repository.AddressRepository;
import VietFreshHub.auth.repository.UserRepository;
import VietFreshHub.customer.dto.CustomerAddressRequest;
import VietFreshHub.vietmap.dto.VietMapPlaceResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Transactional(readOnly = true)
public class CustomerAddressService {

    private final AddressRepository addressRepository;
    private final UserRepository userRepository;
    private final EntityManager entityManager;

    public CustomerAddressService(AddressRepository addressRepository, UserRepository userRepository,
                                  EntityManager entityManager) {
        this.addressRepository = addressRepository;
        this.userRepository = userRepository;
        this.entityManager = entityManager;
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

    public Address getDeliveryAddress(Long customerId, Long addressId) {
        Address address = findOwnedAddress(addressId, customerId);
        if (!isDeliveryLocationComplete(address)) {
            throw new IllegalArgumentException("Địa chỉ nhận hàng chưa đủ thông tin vị trí. Vui lòng cập nhật địa chỉ bằng gợi ý VietMap.");
        }
        return address;
    }

    public boolean requiresLocationVerification(String authenticatedEmail, CustomerAddressRequest request) {
        Long userId = findCurrentUser(authenticatedEmail).getUserId();
        return request.getAddressId() == null
                || requiresLocationVerification(findOwnedAddress(request.getAddressId(), userId), request);
    }

    @Transactional
    public void saveAddress(String authenticatedEmail, CustomerAddressRequest request, VietMapPlaceResponse place) {
        User user = findCurrentUser(authenticatedEmail);
        Address address = request.getAddressId() == null
                ? new Address()
                : findOwnedAddress(request.getAddressId(), user.getUserId());

        if (requiresLocationVerification(address, request)) {
            if (request.getRefId() == null || request.getRefId().isBlank()) {
                throw new IllegalArgumentException("Vui lòng chọn địa chỉ từ danh sách gợi ý.");
            }
            validateDeliveryLocation(place);
            applyLocation(address, place);
        }

        if (request.isDefault()) {
            clearDefaultAddresses(user.getUserId(), address.getAddressId());
        }

        address.setUser(user);
        address.setRecipientName(request.getRecipientName().trim());
        address.setPhone(request.getPhone().trim());
        address.setIsDefault(request.isDefault());
        addressRepository.save(address);
    }

    @Transactional
    public Long createCheckoutAddress(Long customerId, String recipientName, String phone,
                                      VietMapPlaceResponse place) {
        validateDeliveryLocation(place);
        User customer = userRepository.findByUserIdForUpdate(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Tài khoản không hợp lệ."));
        entityManager.refresh(customer, LockModeType.PESSIMISTIC_WRITE);
        if (!"ACTIVE".equals(customer.getStatus())) {
            throw new IllegalArgumentException("Tài khoản không còn được phép thêm địa chỉ.");
        }
        Address address = new Address();
        address.setUser(customer);
        address.setRecipientName(recipientName.trim());
        address.setPhone(phone.trim());
        applyLocation(address, place);
        address.setIsDefault(addressRepository.findAllByUser_UserIdOrderByIsDefaultDescAddressIdAsc(customerId).isEmpty());
        return addressRepository.save(address).getAddressId();
    }

    public void validateDeliveryLocation(VietMapPlaceResponse place) {
        if (place == null) {
            throw new IllegalArgumentException("Không thể xác minh địa chỉ. Vui lòng thử lại.");
        }
        validateAddressComponent(place.province(), 100, true);
        validateAddressComponent(place.district(), 100, false);
        validateAddressComponent(place.ward(), 100, true);
        validateAddressComponent(place.addressLine(), 500, true);
        validateAddressComponent(place.formattedAddress(), 1000, false);
        if (place.formattedAddress() != null && place.formattedAddress().isBlank()) {
            throw new IllegalArgumentException("Địa chỉ đầy đủ đã chọn không hợp lệ. Vui lòng chọn địa chỉ khác.");
        }
        validateAdministrativeId(place.provinceId());
        validateAdministrativeId(place.districtId());
        validateAdministrativeId(place.wardId());
        validateCoordinate(place.latitude(), 90);
        validateCoordinate(place.longitude(), 180);
    }

    public boolean isDeliveryLocationComplete(Address address) {
        try {
            validateDeliveryLocation(new VietMapPlaceResponse(
                    address.getProvinceId(), address.getProvince(), address.getDistrictId(), address.getDistrict(),
                    address.getWardId(), address.getWard(), address.getAddressLine(), address.getFormattedAddress(),
                    address.getLatitude(), address.getLongitude()));
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private void applyLocation(Address address, VietMapPlaceResponse place) {
        address.setProvinceId(place.provinceId());
        address.setProvince(place.province());
        address.setDistrictId(place.districtId());
        address.setDistrict(place.district());
        address.setWardId(place.wardId());
        address.setWard(place.ward());
        address.setAddressLine(place.addressLine());
        address.setFormattedAddress(place.formattedAddress());
        address.setLatitude(place.latitude().setScale(6, RoundingMode.HALF_UP));
        address.setLongitude(place.longitude().setScale(6, RoundingMode.HALF_UP));
    }

    private void validateAddressComponent(String value, int maximumLength, boolean required) {
        if ((required && (value == null || value.isBlank()))
                || (value != null && value.length() > maximumLength)) {
            throw new IllegalArgumentException("Địa chỉ đã chọn chưa đủ thông tin hoặc không hợp lệ. Vui lòng chọn địa chỉ chi tiết hơn.");
        }
    }

    private void validateAdministrativeId(Integer id) {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("Mã đơn vị hành chính của địa chỉ đã chọn không hợp lệ.");
        }
    }

    private void validateCoordinate(BigDecimal coordinate, int limit) {
        if (coordinate == null || coordinate.abs().compareTo(BigDecimal.valueOf(limit)) > 0) {
            throw new IllegalArgumentException("Tọa độ của địa chỉ đã chọn bị thiếu hoặc không hợp lệ.");
        }
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
                .filter(user -> "ACTIVE".equals(user.getStatus()))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản đang đăng nhập hoặc tài khoản không còn hoạt động."));
    }

    private boolean requiresLocationVerification(Address address, CustomerAddressRequest request) {
        return address.getAddressId() == null
                || (request.getRefId() != null && !request.getRefId().isBlank())
                || !Objects.equals(request.getSearchText(), addressText(address));
    }

    private String addressText(Address address) {
        if (address.getFormattedAddress() != null && !address.getFormattedAddress().isBlank()) {
            return address.getFormattedAddress();
        }
        return Stream.of(address.getAddressLine(), address.getWard(), address.getDistrict(), address.getProvince())
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(", "));
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
        request.setFormattedAddress(address.getFormattedAddress());
        request.setSearchText(addressText(address));
        request.setLocationComplete(isDeliveryLocationComplete(address));
        request.setDefault(Boolean.TRUE.equals(address.getIsDefault()));
        return request;
    }
}
