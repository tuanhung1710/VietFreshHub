package VietFreshHub.shop.service;

import VietFreshHub.shop.entity.Shop;
import VietFreshHub.shop.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import VietFreshHub.exception.ResourceNotFoundException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShopProfileService {
    private final ShopService shopService;
    private final ShopRepository shops;

    public Shop getManagedProfile(Authentication authentication) {
        return shops.findById(shopService.getManagedShopId(authentication))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy cửa hàng."));
    }
}
