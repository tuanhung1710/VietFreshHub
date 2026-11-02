package VietFreshHub.admin.service;

import VietFreshHub.admin.dto.AdminShopRow;
import VietFreshHub.shop.entity.Shop;
import VietFreshHub.shop.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminShopService {

    private static final int PAGE_SIZE = 10;
    private static final String ACTIVE = "ACTIVE";
    private static final String INACTIVE = "INACTIVE";
    private static final Set<String> FILTER_STATUSES = Set.of("ALL", ACTIVE, INACTIVE);

    private final ShopRepository shopRepository;

    public Page<AdminShopRow> search(String keyword, String requestedStatus, int requestedPage) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        String selectedStatus = normalizeFilterStatus(requestedStatus);
        int pageNumber = Math.max(requestedPage - 1, 0);
        PageRequest pageRequest = PageRequest.of(pageNumber, PAGE_SIZE,
                Sort.by(Sort.Order.asc("shopName"), Sort.Order.asc("shopId")));

        return shopRepository.searchForAdmin(normalizedKeyword, selectedStatus, pageRequest)
                .map(AdminShopRow::from);
    }

    @Transactional
    public void changeStatus(Long shopId, String requestedStatus) {
        String newStatus = requestedStatus == null
                ? ""
                : requestedStatus.trim().toUpperCase(Locale.ROOT);
        if (!Set.of(ACTIVE, INACTIVE).contains(newStatus)) {
            throw new IllegalArgumentException("Trạng thái shop không hợp lệ.");
        }

        Shop shop = shopRepository.findByShopIdForUpdate(shopId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy shop."));
        if (newStatus.equals(shop.getStatus())) {
            return;
        }

        shop.setStatus(newStatus);
        shopRepository.save(shop);
    }

    public String normalizeFilterStatus(String status) {
        if (status == null || status.isBlank()) {
            return "ALL";
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        return FILTER_STATUSES.contains(normalized) ? normalized : "ALL";
    }
}
