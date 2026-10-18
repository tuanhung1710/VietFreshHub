package VietFreshHub.shop.service;

import VietFreshHub.shop.entity.ShopLocation;
import VietFreshHub.shop.repository.ShopLocationRepository;
import VietFreshHub.shop.repository.ShopRepository;
import VietFreshHub.vietmap.dto.VietMapPlaceResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import VietFreshHub.auth.service.AuthService;
import VietFreshHub.shop.entity.ShopMember;
import VietFreshHub.shop.repository.ShopMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ShopService {

    private final ShopLocationRepository shopLocationRepository;
    private final ShopRepository shopRepository;
    private final EntityManager entityManager;

    private final AuthService authService;
    private final ShopMemberRepository shopMemberRepository;

    @Transactional(readOnly = true)
    public Long getManagedShopId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getAuthorities().stream()
                .noneMatch(authority -> "ROLE_STORE_MANAGER".equals(authority.getAuthority()))) {
            throw new AccessDeniedException("Bạn không có quyền quản lý cửa hàng.");
        }

        Long userId = authService.getCurrentUserId(authentication);
        // Validate both sides in one query, including active legacy/unknown roles.
        ShopMember ownership = shopMemberRepository.findSoleActiveOwnerByUserId(userId)
                .orElseThrow(() -> new AccessDeniedException(
                        "Tài khoản phải là chủ duy nhất đang hoạt động của một cửa hàng."));

        return ownership.getShopId();
    }
    @Transactional(readOnly = true)
    public String getManagedShopCode(Authentication authentication) {
        return VietFreshHub.common.BusinessCodes.format("SHP", getManagedShopId(authentication));
    }

    @Transactional(readOnly = true)
    public List<Long> findActiveManagerUserIds(Long shopId) {
        return shopMemberRepository.findActiveManagerUserIdsByShopId(shopId);
    }

    @Transactional(readOnly = true)
    public Optional<ShopLocation> findLocationByShopId(Long shopId) {
        return shopLocationRepository.findById(shopId);
    }

    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Optional<ShopLocation> findLocationByShopIdForConfirmation(Long shopId) {
        Optional<ShopLocation> location = shopLocationRepository.findById(shopId);
        location.ifPresent(value -> entityManager.refresh(value, LockModeType.PESSIMISTIC_READ));
        return location;
    }

    public void validatePickupLocation(VietMapPlaceResponse place) {
        if (place == null) {
            throw new IllegalArgumentException("Không xác minh được địa chỉ đã chọn. Vui lòng chọn lại địa chỉ.");
        }
        validateAddressComponent(place.province(), "tỉnh/thành phố", 100, true);
        validateAddressComponent(place.district(), "quận/huyện", 100, false);
        validateAddressComponent(place.ward(), "phường/xã", 100, true);
        validateAddressComponent(place.addressLine(), "địa chỉ chi tiết", 500, true);
        validateAddressComponent(place.formattedAddress(), "địa chỉ đầy đủ", 1000, false);
        validateAdministrativeId(place.provinceId());
        validateAdministrativeId(place.districtId());
        validateAdministrativeId(place.wardId());
        validateCoordinate(place.latitude(), 90, "Vĩ độ");
        validateCoordinate(place.longitude(), 180, "Kinh độ");
    }

    @Transactional
    public void savePickupLocation(Authentication authentication, VietMapPlaceResponse place) {
        Long shopId = getManagedShopId(authentication);
        validatePickupLocation(place);
        shopRepository.findByShopIdForUpdate(shopId)
                .orElseThrow(() -> new AccessDeniedException("Không xác định được cửa hàng bạn được phép quản lý."));
        ShopLocation location = shopLocationRepository.findById(shopId)
                .orElseGet(() -> new ShopLocation(shopId));
        location.updateLocation(
                place.provinceId(), place.province(), place.districtId(), place.district(),
                place.wardId(), place.ward(), place.addressLine(), place.formattedAddress(),
                place.latitude().setScale(6, RoundingMode.HALF_UP),
                place.longitude().setScale(6, RoundingMode.HALF_UP)
        );
        shopLocationRepository.save(location);
    }

    private void validateAddressComponent(String value, String label, int maximumLength, boolean required) {
        if (required && (value == null || value.isBlank())) {
            throw new IllegalArgumentException("Địa chỉ đã chọn thiếu " + label
                    + ". Vui lòng chọn địa điểm chi tiết hơn.");
        }
        if (value != null && value.length() > maximumLength) {
            throw new IllegalArgumentException("Thông tin " + label + " của địa chỉ đã chọn vượt quá "
                    + maximumLength + " ký tự. Vui lòng chọn địa chỉ khác.");
        }
    }

    private void validateAdministrativeId(Integer id) {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("Mã đơn vị hành chính của địa chỉ đã chọn không hợp lệ.");
        }
    }

    private void validateCoordinate(BigDecimal coordinate, int limit, String label) {
        if (coordinate == null || coordinate.abs().compareTo(BigDecimal.valueOf(limit)) > 0) {
            throw new IllegalArgumentException(label + " của địa chỉ đã chọn bị thiếu hoặc không hợp lệ.");
        }
    }
}
