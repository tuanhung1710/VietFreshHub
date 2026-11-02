package VietFreshHub.review.service;

import VietFreshHub.auth.entity.User;
import VietFreshHub.auth.service.AuthService;
import VietFreshHub.auth.service.CurrentCustomerService;
import VietFreshHub.catalog.entity.CatalogProduct;
import VietFreshHub.catalog.repository.CatalogProductRepository;
import VietFreshHub.notification.services.NotificationService;
import VietFreshHub.order.entity.OrderItem;
import VietFreshHub.order.entity.ShopOrder;
import VietFreshHub.order.repository.OrderItemRepository;
import VietFreshHub.order.repository.ShopOrderRepository;
import VietFreshHub.review.dto.ReviewCard;
import VietFreshHub.review.dto.ReviewForm;
import VietFreshHub.review.dto.ReviewReportCard;
import VietFreshHub.review.dto.ReviewReportForm;
import VietFreshHub.review.entity.Review;
import VietFreshHub.review.entity.ReviewReport;
import VietFreshHub.review.entity.ReviewReportStatus;
import VietFreshHub.review.entity.ReviewStatus;
import VietFreshHub.review.repository.ReviewReportRepository;
import VietFreshHub.review.repository.ReviewRepository;
import VietFreshHub.shop.entity.Shop;
import VietFreshHub.shop.repository.ShopRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class ReviewService {
    private final ReviewRepository reviews;
    private final ReviewReportRepository reports;
    private final OrderItemRepository orderItems;
    private final ShopOrderRepository shopOrders;
    private final CatalogProductRepository products;
    private final ShopRepository shops;
    private final CurrentCustomerService customers;
    private final AuthService auth;
    private final NotificationService notifications;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public Page<ReviewCard> productReviews(Long productId, int page) {
        requireProduct(productId);
        return reviews.findByProduct_ProductIdAndStatusOrderByCreatedAtDesc(
                productId, ReviewStatus.APPROVED, PageRequest.of(Math.max(0, page), 10)).map(this::card);
    }

    @Transactional(readOnly = true)
    public ReviewRepository.RatingSummary productRating(Long productId) {
        requireProduct(productId);
        return reviews.productRating(productId, ReviewStatus.APPROVED);
    }

    @Transactional(readOnly = true)
    public Page<ReviewCard> shopReviews(Long shopId, int page) {
        if (shopId == null || !shops.existsById(shopId)) throw notFound("Không tìm thấy cửa hàng.");
        return reviews.findByShop_ShopIdAndStatusOrderByCreatedAtDesc(
                shopId, ReviewStatus.APPROVED, PageRequest.of(Math.max(0, page), 10)).map(this::card);
    }

    @Transactional(readOnly = true)
    public Page<ReviewCard> myReviews(Authentication authentication, int page) {
        Long userId = customers.requireCustomerId(authentication);
        return reviews.findByUser_UserIdOrderByCreatedAtDesc(userId, PageRequest.of(Math.max(0, page), 10))
                .map(this::card);
    }

    @Transactional(readOnly = true)
    public Page<ReviewCard> pendingReviews(int page) {
        return reviews.findByStatusOrderByCreatedAtAsc(ReviewStatus.PENDING,
                PageRequest.of(Math.max(0, page), 20)).map(this::card);
    }

    @Transactional(readOnly = true)
    public ReviewRepository.RatingSummary shopRating(Long shopId) {
        if (shopId == null || !shops.existsById(shopId)) throw notFound("Không tìm thấy cửa hàng.");
        return reviews.shopRating(shopId, ReviewStatus.APPROVED);
    }

    @Transactional(readOnly = true)
    public java.util.List<Long> eligibleOrderItems(Authentication authentication, Long productId) {
        Long userId = customers.requireCustomerId(authentication);
        requireProduct(productId);
        var eligibleIds = orderItems.findByShopOrder_Order_Customer_UserIdAndShopOrder_StatusAndVariant_Product_ProductId(
                userId, "COMPLETED", productId).stream().map(OrderItem::getOrderItemId).toList();
        if (eligibleIds.isEmpty()) return java.util.List.of();
        var reviewedIds = reviews.findByUser_UserIdAndOrderItem_OrderItemIdIn(userId, eligibleIds).stream()
                .map(review -> review.getOrderItem().getOrderItemId()).collect(java.util.stream.Collectors.toSet());
        return eligibleIds.stream().filter(id -> !reviewedIds.contains(id)).toList();
    }

    @Transactional(readOnly = true)
    public java.util.List<Long> eligibleShopOrders(Authentication authentication, Long shopId) {
        Long userId = customers.requireCustomerId(authentication);
        if (shopId == null || !shops.existsById(shopId)) throw notFound("Không tìm thấy cửa hàng.");
        var eligibleIds = shopOrders.findByOrder_Customer_UserIdAndShopIdAndStatus(userId, shopId, "COMPLETED")
                .stream().map(ShopOrder::getShopOrderId).toList();
        if (eligibleIds.isEmpty()) return java.util.List.of();
        var reviewedIds = reviews.findByUser_UserIdAndShopOrder_ShopOrderIdIn(userId, eligibleIds).stream()
                .map(review -> review.getShopOrder().getShopOrderId()).collect(java.util.stream.Collectors.toSet());
        return eligibleIds.stream().filter(id -> !reviewedIds.contains(id)).toList();
    }

    @Transactional
    public void createProductReview(Authentication authentication, Long productId, Long orderItemId, ReviewForm form) {
        Long userId = customers.requireCustomerId(authentication);
        validate(form);
        CatalogProduct product = requireProduct(productId);
        OrderItem item = orderItems.findById(orderItemId)
                .orElseThrow(() -> notFound("Không tìm thấy sản phẩm đã mua."));
        if (!userId.equals(item.getShopOrder().getOrder().getCustomer().getUserId())
                || !"COMPLETED".equals(item.getShopOrder().getStatus())
                || !productId.equals(item.getVariant().getProduct().getProductId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sản phẩm chưa đủ điều kiện đánh giá.");
        }
        if (reviews.existsByUser_UserIdAndOrderItem_OrderItemId(userId, orderItemId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đơn hàng này đã được đánh giá.");
        }
        Review review = new Review();
        review.setUser(entityManager.getReference(User.class, userId));
        review.setProduct(product);
        review.setOrderItem(item);
        review.setRating(form.rating().byteValue());
        review.setContent(clean(form.content()));
        reviews.save(review);
    }

    @Transactional
    public void createShopReview(Authentication authentication, Long shopId, Long shopOrderId, ReviewForm form) {
        Long userId = customers.requireCustomerId(authentication);
        validate(form);
        Shop shop = shops.findById(shopId).orElseThrow(() -> notFound("Không tìm thấy cửa hàng."));
        ShopOrder purchase = shopOrders.findById(shopOrderId)
                .orElseThrow(() -> notFound("Không tìm thấy đơn hàng cửa hàng."));
        if (!userId.equals(purchase.getOrder().getCustomer().getUserId())
                || !shopId.equals(purchase.getShopId()) || !"COMPLETED".equals(purchase.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Đơn hàng chưa đủ điều kiện đánh giá cửa hàng.");
        }
        if (reviews.existsByUser_UserIdAndShopOrder_ShopOrderId(userId, shopOrderId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đơn hàng này đã được đánh giá.");
        }
        Review review = new Review();
        review.setUser(entityManager.getReference(User.class, userId));
        review.setShop(shop);
        review.setShopOrder(purchase);
        review.setRating(form.rating().byteValue());
        review.setContent(clean(form.content()));
        reviews.save(review);
    }

    @Transactional
    public void edit(Authentication authentication, Long reviewId, ReviewForm form) {
        Long userId = customers.requireCustomerId(authentication);
        validate(form);
        Review review = reviews.findByReviewIdAndUser_UserId(reviewId, userId)
                .orElseThrow(() -> notFound("Không tìm thấy đánh giá của bạn."));
        if (review.getStatus() != ReviewStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Chính sách chỉnh sửa đánh giá sau khi đã kiểm duyệt đang TBD.");
        }
        review.setRating(form.rating().byteValue());
        review.setContent(clean(form.content()));
    }

    @Transactional
    public void delete(Authentication authentication, Long reviewId) {
        Long userId = customers.requireCustomerId(authentication);
        Review review = reviews.findByReviewIdAndUser_UserId(reviewId, userId)
                .orElseThrow(() -> notFound("Không tìm thấy đánh giá của bạn."));
        reports.deleteByReview_ReviewId(reviewId);
        reviews.delete(review);
    }

    @Transactional
    public void report(Authentication authentication, Long reviewId, ReviewReportForm form) {
        Long userId = customers.requireCustomerId(authentication);
        Review review = reviews.findById(reviewId).orElseThrow(() -> notFound("Không tìm thấy đánh giá."));
        if (review.getStatus() != ReviewStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Chỉ có thể báo cáo đánh giá đang hiển thị.");
        }
        ReviewReport report = new ReviewReport();
        report.setReview(review);
        report.setReporter(entityManager.getReference(User.class, userId));
        report.setReason(form.reason().strip());
        report.setDescription(clean(form.description()));
        reports.save(report);
    }

    @Transactional(readOnly = true)
    public Page<ReviewReportCard> pendingReports(int page) {
        return reports.findByStatusOrderByCreatedAtAsc(ReviewReportStatus.PENDING,
                PageRequest.of(Math.max(0, page), 20)).map(this::reportCard);
    }

    @Transactional
    public void moderate(Authentication authentication, Long reportId, String action) {
        Long adminId = auth.getCurrentUserId(authentication);
        ReviewReport report = reports.findById(reportId)
                .orElseThrow(() -> notFound("Không tìm thấy báo cáo đánh giá."));
        if (report.getStatus() != ReviewReportStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Báo cáo đã được xử lý.");
        }
        Review review = report.getReview();
        ReviewStatus nextReviewStatus;
        ReviewReportStatus nextReportStatus;
        String message;
        switch (action) {
            case "APPROVE" -> {
                nextReviewStatus = ReviewStatus.APPROVED;
                nextReportStatus = ReviewReportStatus.ACTION_TAKEN;
                message = "Đánh giá của bạn đã được duyệt.";
            }
            case "REJECT" -> {
                nextReviewStatus = ReviewStatus.REJECTED;
                nextReportStatus = ReviewReportStatus.ACTION_TAKEN;
                message = "Đánh giá của bạn đã bị từ chối.";
            }
            case "REMOVE" -> {
                nextReviewStatus = ReviewStatus.REMOVED;
                nextReportStatus = ReviewReportStatus.ACTION_TAKEN;
                message = "Đánh giá của bạn đã bị gỡ sau khi xem xét báo cáo.";
            }
            case "DISMISS" -> {
                report.setStatus(ReviewReportStatus.DISMISSED);
                report.setReviewedBy(entityManager.getReference(User.class, adminId));
                report.setReviewedAt(LocalDateTime.now(ZoneOffset.UTC).withNano(0));
                return;
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thao tác kiểm duyệt không hợp lệ.");
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).withNano(0);
        applyModeration(review, nextReviewStatus, adminId, now);
        report.setStatus(nextReportStatus);
        report.setReviewedBy(entityManager.getReference(User.class, adminId));
        report.setReviewedAt(now);
        notifications.send(review.getUser().getUserId(), "REVIEW_MODERATION", "Cập nhật đánh giá",
                message, "REVIEW", review.getReviewId());
    }

    @Transactional
    public void moderateReview(Authentication authentication, Long reviewId, String action) {
        Long adminId = auth.getCurrentUserId(authentication);
        Review review = reviews.findById(reviewId).orElseThrow(() -> notFound("Không tìm thấy đánh giá."));
        ReviewStatus status = switch (action) {
            case "APPROVE" -> ReviewStatus.APPROVED;
            case "REJECT" -> ReviewStatus.REJECTED;
            case "REMOVE" -> ReviewStatus.REMOVED;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thao tác kiểm duyệt không hợp lệ.");
        };
        applyModeration(review, status, adminId, LocalDateTime.now(ZoneOffset.UTC).withNano(0));
        String text = switch (status) {
            case APPROVED -> "Đánh giá của bạn đã được duyệt.";
            case REJECTED -> "Đánh giá của bạn đã bị từ chối.";
            case REMOVED -> "Đánh giá của bạn đã bị gỡ.";
            default -> throw new IllegalStateException();
        };
        notifications.send(review.getUser().getUserId(), "REVIEW_MODERATION", "Cập nhật đánh giá",
                text, "REVIEW", reviewId);
    }

    private CatalogProduct requireProduct(Long productId) {
        return products.findById(productId).orElseThrow(() -> notFound("Không tìm thấy sản phẩm."));
    }

    private ReviewCard card(Review review) {
        return new ReviewCard(review.getReviewId(), review.getUser().getFullName(), targetName(review),
                review.getRating(), review.getContent(), review.getStatus(), review.getCreatedAt());
    }

    private String targetName(Review review) {
        return review.getProduct() != null ? review.getProduct().getName() : review.getShop().getShopName();
    }

    private void applyModeration(Review review, ReviewStatus status, Long adminId, LocalDateTime at) {
        review.setStatus(status);
        review.setModeratedBy(entityManager.getReference(User.class, adminId));
        review.setModeratedAt(at);
    }

    private ReviewReportCard reportCard(ReviewReport report) {
        return new ReviewReportCard(report.getReportId(), report.getReview().getReviewId(),
                report.getReporter().getFullName(),
                report.getReview().getProduct() != null ? report.getReview().getProduct().getName()
                        : report.getReview().getShop().getShopName(),
                report.getReview().getRating(), report.getReview().getContent(), report.getReason(),
                report.getDescription(), report.getStatus(), report.getCreatedAt());
    }

    private void validate(ReviewForm form) {
        if (form == null || form.rating() == null || form.rating() < 1 || form.rating() > 5
                || (form.content() != null && form.content().length() > 3000)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đánh giá không hợp lệ.");
        }
    }

    private String clean(String text) { return text == null || text.isBlank() ? null : text.strip(); }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
