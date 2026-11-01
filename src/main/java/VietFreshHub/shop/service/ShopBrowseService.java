package VietFreshHub.shop.service;

import VietFreshHub.shop.entity.Shop;
import VietFreshHub.shop.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShopBrowseService {
    private final ShopRepository shops;

    public Page<Shop> list(int page, String keyword) {
        if (page < 1 || page > 10000 || keyword == null || keyword.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trang hoặc từ khóa không hợp lệ.");
        }
        return shops.findPublicShops(keyword.strip(), PageRequest.of(page - 1, 12, Sort.by("shopName", "shopId")));
    }
}
