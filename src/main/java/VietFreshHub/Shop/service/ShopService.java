package VietFreshHub.Shop.service;

import VietFreshHub.Auth.service.AuthService;
import VietFreshHub.Shop.entity.ShopMember;
import VietFreshHub.Shop.repository.ShopMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ShopService {

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
        List<ShopMember> memberships = shopMemberRepository.findByUserIdAndStatusAndMemberRoleIn(
                userId, "ACTIVE", List.of("OWNER", "MANAGER"));

        if (memberships.size() != 1) {
            throw new AccessDeniedException("Không xác định được cửa hàng bạn được phép quản lý.");
        }

        return memberships.get(0).getShopId();
    }
}
