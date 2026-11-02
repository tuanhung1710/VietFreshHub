package VietFreshHub.shop.service;

import VietFreshHub.shop.repository.ShopReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShopReviewService {
    private final ShopBrowseService shops;
    private final ShopReviewRepository reviews;

    public ShopReviewRepository.Summary summary(Long shopId) {
        shops.profile(shopId);
        return reviews.summary(shopId);
    }

    public Page<ShopReviewRepository.Review> list(Long shopId, Integer rating, int page) {
        shops.profile(shopId);
        if (page < 1 || page > 10000 || rating != null && (rating < 1 || rating > 5)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trang hoặc số sao không hợp lệ.");
        }
        return reviews.list(shopId, rating, page);
    }
}
