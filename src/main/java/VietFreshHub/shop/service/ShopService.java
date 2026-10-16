package VietFreshHub.shop.service;

import VietFreshHub.auth.service.AuthService;
import VietFreshHub.shop.entity.ShopMember;
import VietFreshHub.shop.repository.ShopMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
