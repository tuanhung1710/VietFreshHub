package VietFreshHub.shop.service;

import VietFreshHub.auth.repository.UserRepository;
import VietFreshHub.auth.service.CurrentCustomerService;
import VietFreshHub.shop.entity.Shop;
import VietFreshHub.shop.repository.ShopFavoriteRepository;
import VietFreshHub.shop.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
@Transactional(readOnly = true)
public class ShopFavoriteService {
    private final CurrentCustomerService customers;
    private final UserRepository users;
    private final ShopBrowseService shops;
    private final ShopRepository shopRepository;
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

    public Page<Shop> list(Authentication authentication, int page) {
        Long userId = customers.requireCustomerId(authentication);
        if (page < 1 || page > 10000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trang không hợp lệ.");
        var ids = favorites.list(userId, page);
        var byId = shopRepository.findAllById(ids.getContent()).stream().collect(Collectors.toMap(Shop::getShopId, Function.identity()));
        return ids.map(byId::get);
    }
}
