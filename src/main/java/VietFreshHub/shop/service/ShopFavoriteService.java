package VietFreshHub.shop.service;

import VietFreshHub.auth.repository.UserRepository;
import VietFreshHub.auth.service.CurrentCustomerService;
import VietFreshHub.shop.repository.ShopFavoriteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
@Transactional(readOnly = true)
public class ShopFavoriteService {
    private final CurrentCustomerService customers;
    private final UserRepository users;
    private final ShopBrowseService shops;
    private final ShopFavoriteRepository favorites;

    @Transactional
    public void setFavorite(Authentication authentication, Long shopId, boolean favorite) {
        Long userId = customers.requireCustomerId(authentication);
        // Serialize requests by the customer so repeated follow requests cannot create duplicates.
        var user = users.findByUserIdForUpdate(userId).orElseThrow(() -> new AccessDeniedException("Tài khoản không hợp lệ."));
        if (!"ACTIVE".equals(user.getStatus())) throw new AccessDeniedException("Tài khoản không còn hoạt động.");
        if (!favorite) {
            favorites.remove(userId, shopId);
        } else {
            shops.profile(shopId);
            if (!favorites.exists(userId, shopId)) favorites.add(userId, shopId);
        }
    }
}
