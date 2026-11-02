package VietFreshHub.shop.service;

import VietFreshHub.shop.repository.ShopOfferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShopOfferService {
    private final ShopBrowseService shops;
    private final ShopOfferRepository offers;

    public Page<ShopOfferRepository.Offer> list(Long shopId, int page) {
        shops.profile(shopId);
        if (page < 1 || page > 10000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trang không hợp lệ.");
        return offers.list(shopId, page);
    }
}
