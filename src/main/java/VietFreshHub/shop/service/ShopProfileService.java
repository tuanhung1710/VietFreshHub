package VietFreshHub.shop.service;

import VietFreshHub.shop.entity.Shop;
import VietFreshHub.shop.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import VietFreshHub.exception.ResourceNotFoundException;
import VietFreshHub.shop.dto.ShopProfileRequest;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;

@Service
@RequiredArgsConstructor
@Validated
@Transactional(readOnly = true)
public class ShopProfileService {
    private final ShopService shopService;
    private final ShopRepository shops;

    public Shop getManagedProfile(Authentication authentication) {
        return shops.findById(shopService.getManagedShopId(authentication))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy cửa hàng."));
    }

    @Transactional
    public void update(Authentication authentication, @Valid ShopProfileRequest request) {
        Shop shop = shops.findByShopIdForUpdate(shopService.getManagedShopId(authentication))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy cửa hàng."));
        shop.setShopName(request.getShopName().strip());
        shop.setDescription(trim(request.getDescription()));
        shop.setPhone(trim(request.getPhone()));
        shop.setEmail(trim(request.getEmail()));
        shop.setLogoUrl(trim(request.getLogoUrl()));
        shop.setBannerUrl(trim(request.getBannerUrl()));
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    @Transactional
    public void setAvailability(Authentication authentication, String status) {
        if (!"OPEN".equals(status) && !"CLOSED".equals(status)) {
            throw new IllegalArgumentException("Chỉ chấp nhận trạng thái OPEN hoặc CLOSED.");
        }
        Shop shop = shops.findByShopIdForUpdate(shopService.getManagedShopId(authentication))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy cửa hàng."));
        if (!"ACTIVE".equals(shop.getStatus())) {
            throw new IllegalArgumentException("Cửa hàng chưa hoạt động không thể đổi trạng thái nhận đơn.");
        }
        shop.setAvailabilityStatus(status);
    }
}
