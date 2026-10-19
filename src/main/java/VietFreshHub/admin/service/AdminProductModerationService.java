package VietFreshHub.admin.service;

import VietFreshHub.admin.dto.AdminProductModerationDetail;
import VietFreshHub.admin.dto.AdminProductModerationRow;
import VietFreshHub.admin.repository.AdminProductModerationRepository;
import VietFreshHub.auth.entity.User;
import VietFreshHub.auth.repository.UserRepository;
import VietFreshHub.inventory.service.InventoryJournalService;
import VietFreshHub.notification.service.NotificationService;
import VietFreshHub.product.entity.Product;
import VietFreshHub.product.enums.ApprovalStatus;
import VietFreshHub.product.enums.ProductStatus;
import VietFreshHub.product.repository.ProductImageRepository;
import VietFreshHub.product.repository.ProductVariantRepository;
import VietFreshHub.shop.repository.ShopRepository;
import VietFreshHub.shop.service.ShopService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminProductModerationService {

    private static final int PAGE_SIZE = 25;
    private static final int MAX_PAGE = 10000;
    private static final Set<String> TABS = Set.of("PENDING", "LOCKED", "REVIEWED");
    private static final Set<String> DATE_RANGES = Set.of("ALL", "TODAY", "LAST_3_DAYS", "LAST_7_DAYS", "LAST_30_DAYS");
    private static final ZoneId BUSINESS_TIME_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final AdminProductModerationRepository moderationRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ShopRepository shopRepository;
    private final ShopService shopService;
    private final UserRepository userRepository;
    private final InventoryJournalService journalService;
    private final NotificationService notificationService;

    public Page<AdminProductModerationRow> search(
            String tab, String keyword, String dateRange, int requestedPage) {
        String selectedTab = normalizeTab(tab);
        String normalizedKeyword = normalizeKeyword(keyword);
        DateWindow window = dateWindow(dateRange);
        int page = Math.max(0, Math.min(requestedPage - 1, MAX_PAGE - 1));
        return moderationRepository.search(selectedTab, normalizedKeyword, window.from(), window.until(),
                PageRequest.of(page, PAGE_SIZE));
    }

    @Transactional
    public int bulkApprovePending(String keyword, String dateRange, Long adminId) {
        String normalizedKeyword = normalizeKeyword(keyword);
        DateWindow window = dateWindow(dateRange);
        User admin = userRepository.findById(adminId)
                .filter(user -> "ACTIVE".equalsIgnoreCase(user.getStatus()))
                .orElseThrow(() -> new IllegalStateException("Tài khoản admin không còn hoạt động."));
        LocalDateTime reviewedAt = LocalDateTime.now(ZoneOffset.UTC).withNano(0);
        return moderationRepository.bulkApprovePending(
                normalizedKeyword, window.from(), window.until(), admin.getUserId(), reviewedAt);
    }

    public long countPending() {
        return moderationRepository.countByStatusNotAndApprovalStatusAndModerationBlockedFalse(
                ProductStatus.DELETED, ApprovalStatus.PENDING);
    }

    public long countLocked() {
        return moderationRepository.countByStatusNotAndModerationBlockedTrue(ProductStatus.DELETED);
    }

    public long countReviewed() {
        return moderationRepository.countByStatusNotAndApprovalStatusNotAndModerationBlockedFalse(
                ProductStatus.DELETED, ApprovalStatus.PENDING);
    }

    public AdminProductModerationDetail getDetail(Long productId) {
        Product product = moderationRepository.findById(productId)
                .filter(candidate -> candidate.getStatus() != ProductStatus.DELETED)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy sản phẩm."));
        String shopName = shopRepository.findById(product.getShopId())
                .map(shop -> shop.getShopName())
                .orElse("Cửa hàng không còn tồn tại");
        return new AdminProductModerationDetail(
                product,
                shopName,
                productVariantRepository.findByProductIdOrderByVariantId(productId),
                productImageRepository.findByProductIdOrderBySortOrderAscImageIdAsc(productId));
    }

    @Transactional
    public void review(Long productId, String action, String reason, Long adminId) {
        Product product = moderationRepository.lockByProductId(productId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy sản phẩm."));
        if (product.getStatus() == ProductStatus.DELETED) {
            throw new IllegalStateException("Sản phẩm đã bị xóa, không thể hậu kiểm.");
        }
        User admin = userRepository.findById(adminId)
                .filter(user -> "ACTIVE".equalsIgnoreCase(user.getStatus()))
                .orElseThrow(() -> new IllegalStateException("Tài khoản admin không còn hoạt động."));

        String normalizedAction = action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
        boolean wasModerationBlocked = product.isModerationBlocked();
        switch (normalizedAction) {
            case "APPROVE" -> {
                if (product.isModerationBlocked()) {
                    throw new IllegalStateException("Sản phẩm đang bị khóa; hãy dùng thao tác mở khóa và duyệt.");
                }
                markApproved(product, admin.getUserId());
                journalService.audit(admin.getUserId(), "PRODUCT_APPROVED", "PRODUCT",
                        productId, "Admin đã hậu kiểm và cho phép sản phẩm tiếp tục hiển thị.");
            }
            case "BLOCK" -> {
                String normalizedReason = reason == null ? "" : reason.trim();
                if (normalizedReason.isEmpty()) {
                    throw new IllegalArgumentException("Vui lòng nhập lý do tạm khóa sản phẩm.");
                }
                if (normalizedReason.length() > 1000) {
                    throw new IllegalArgumentException("Lý do tạm khóa tối đa 1000 ký tự.");
                }
                product.setModerationBlocked(true);
                product.setModerationReason(normalizedReason);
                journalService.audit(admin.getUserId(), "PRODUCT_MODERATION_BLOCKED", "PRODUCT",
                        productId, normalizedReason);
                if (!wasModerationBlocked) {
                    notifyShopManagersOfBlock(product, productId, normalizedReason);
                }
            }
            case "UNBLOCK" -> {
                if (!product.isModerationBlocked()) {
                    throw new IllegalStateException("Sản phẩm hiện không bị khóa.");
                }
                product.setModerationBlocked(false);
                product.setModerationReason(null);
                markApproved(product, admin.getUserId());
                journalService.audit(admin.getUserId(), "PRODUCT_MODERATION_UNBLOCKED", "PRODUCT",
                        productId, "Admin đã mở khóa và duyệt lại sản phẩm.");
            }
            default -> throw new IllegalArgumentException("Thao tác hậu kiểm không hợp lệ.");
        }
        product.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC).withNano(0));
        moderationRepository.save(product);
    }

    private void notifyShopManagersOfBlock(Product product, Long productId, String reason) {
        if (product.getShopId() == null) {
            return;
        }
        String productName = product.getName() == null || product.getName().isBlank()
                ? "sản phẩm #" + productId
                : product.getName().trim();
        String content = "Admin đã tạm ẩn sản phẩm \"" + productName
                + "\" khỏi sàn. Lý do: " + reason;
        for (Long managerUserId : shopService.findActiveManagerUserIds(product.getShopId())) {
            notificationService.createInAppForTransition(
                    managerUserId,
                    "PRODUCT_MODERATION_BLOCKED",
                    "Sản phẩm bị tạm ẩn sau kiểm duyệt",
                    content,
                    "PRODUCT",
                    productId
            );
        }
    }

    private void markApproved(Product product, Long adminId) {
        product.setApprovalStatus(ApprovalStatus.APPROVED);
        product.setApprovedBy(adminId);
        product.setApprovedAt(LocalDateTime.now(ZoneOffset.UTC).withNano(0));
    }

    private String normalizeTab(String tab) {
        if (tab == null || tab.isBlank()) return "PENDING";
        String normalized = tab.trim().toUpperCase(Locale.ROOT);
        return TABS.contains(normalized) ? normalized : "PENDING";
    }

    private String normalizeKeyword(String keyword) {
        String normalized = keyword == null ? "" : keyword.trim();
        if (normalized.length() > 100) {
            throw new IllegalArgumentException("Từ khóa tìm kiếm tối đa 100 ký tự.");
        }
        return normalized;
    }

    private DateWindow dateWindow(String dateRange) {
        String normalized = dateRange == null ? "ALL" : dateRange.trim().toUpperCase(Locale.ROOT);
        if (!DATE_RANGES.contains(normalized) || "ALL".equals(normalized)) {
            return new DateWindow(null, null);
        }
        LocalDate today = LocalDate.now(BUSINESS_TIME_ZONE);
        int inclusiveDays = switch (normalized) {
            case "TODAY" -> 1;
            case "LAST_3_DAYS" -> 3;
            case "LAST_7_DAYS" -> 7;
            case "LAST_30_DAYS" -> 30;
            default -> throw new IllegalArgumentException("Khoảng ngày lọc không hợp lệ.");
        };
        LocalDateTime fromUtc = today.minusDays(inclusiveDays - 1L).atStartOfDay(BUSINESS_TIME_ZONE)
                .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        LocalDateTime untilUtc = LocalDateTime.now(ZoneOffset.UTC);
        return new DateWindow(fromUtc, untilUtc);
    }

    private record DateWindow(LocalDateTime from, LocalDateTime until) {}
}
