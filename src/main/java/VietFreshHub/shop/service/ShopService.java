package VietFreshHub.shop.service;

import VietFreshHub.shop.entity.ShopMember;
import VietFreshHub.shop.repository.ShopMemberRepository;
import VietFreshHub.auth.repository.UserRepository;
import VietFreshHub.shop.entity.ShopLocation;
import VietFreshHub.shop.repository.ShopLocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ShopService {

    private final UserRepository userRepository;
    private final ShopMemberRepository shopMemberRepository;
    private final ShopLocationRepository shopLocationRepository;

    @Transactional(readOnly = true)
    public Long getManagedShopId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getAuthorities().stream()
                .noneMatch(authority -> "ROLE_STORE_MANAGER".equals(authority.getAuthority()))) {
            throw new AccessDeniedException("Bạn không có quyền quản lý cửa hàng.");
        }

        Long userId = userRepository.findByEmailIgnoreCase(authentication.getName())
                .map(user -> user.getUserId())
                .orElseThrow(() -> new AccessDeniedException("Không xác định được tài khoản."));
        List<ShopMember> memberships = shopMemberRepository.findActiveMembershipsByUserId(userId).stream()
                .filter(membership -> List.of("OWNER", "MANAGER").contains(membership.getMemberRole()))
                .toList();

        if (memberships.size() != 1) {
            throw new AccessDeniedException("Không xác định được cửa hàng bạn được phép quản lý.");
        }

        return memberships.get(0).getShop().getShopId();
    }

    @Transactional(readOnly = true)
    public Optional<ShopLocation> findLocationByShopId(Long shopId) {
        return shopLocationRepository.findById(shopId);
    }
}
