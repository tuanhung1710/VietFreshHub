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
import VietFreshHub.review.dto.ReviewPurchaseOption;
import VietFreshHub.review.dto.ShopReplyForm;
import VietFreshHub.review.entity.Review;
import VietFreshHub.review.entity.ReviewReport;
import VietFreshHub.review.entity.ReviewReportStatus;
import VietFreshHub.review.entity.ReviewStatus;
import VietFreshHub.review.repository.ReviewReportRepository;
import VietFreshHub.review.repository.ReviewRepository;
import VietFreshHub.shop.entity.Shop;
import VietFreshHub.shop.repository.ShopRepository;
import VietFreshHub.shop.service.ShopService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
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
    private final ShopService shopService;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public Page<ReviewCard> productReviews(Long productId, int page) {
        return productReviews(productId, page, null, false);
    }

    @Transactional(readOnly = true)
    public Page<ReviewCard> productReviews(Long productId, int page, Byte rating, boolean oldestFirst) {
        requireProduct(productId);
        return reviews.findProductReviews(productId, ReviewStatus.APPROVED, rating,
                PageRequest.of(Math.max(0, page), 10, org.springframework.data.domain.Sort.by(
                        oldestFirst ? org.springframework.data.domain.Sort.Direction.ASC : org.springframework.data.domain.Sort.Direction.DESC,
                        "createdAt", "reviewId"))).map(this::card);
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
    public Page<ReviewCard> shopProductReviews(Long shopId, int page) {
        if (shopId == null || !shops.existsById(shopId)) throw notFound("Không tìm thấy cửa hàng.");
        return reviews.findByProduct_Shop_ShopIdAndStatusOrderByCreatedAtDesc(
                shopId, ReviewStatus.APPROVED, PageRequest.of(Math.max(0, page), 10)).map(this::shopProductCard);
    }

    @Transactional(readOnly = true)
    public Page<ReviewCard> myReviews(Authentication authentication, int page) {
        return myReviews(authentication, page, null, null, false);
    }

    @Transactional(readOnly = true)
    public Page<ReviewCard> myReviews(Authentication authentication, int page, Byte rating, Long productId, boolean oldestFirst) {
        Long userId = customers.requireCustomerId(authentication);
        return reviews.findMyReviews(userId, rating, productId, PageRequest.of(Math.max(0, page), 10,
                        org.springframework.data.domain.Sort.by(oldestFirst ? org.springframework.data.domain.Sort.Direction.ASC
                                : org.springframework.data.domain.Sort.Direction.DESC, "createdAt", "reviewId")))
                .map(this::card);
    }

    @Transactional(readOnly = true)
    public java.util.List<ReviewRepository.ProductOption> reviewedProducts(Authentication authentication) {
        return reviews.findReviewedProducts(customers.requireCustomerId(authentication));
    }

    @Transactional(readOnly = true)
    public ReviewRepository.RatingSummary shopRating(Long shopId) {
        if (shopId == null || !shops.existsById(shopId)) throw notFound("Không tìm thấy cửa hàng.");
        return reviews.shopRating(shopId, ReviewStatus.APPROVED);
    }

    @Transactional(readOnly = true)
    public String shopName(Long shopId) {
        return shops.findById(shopId).orElseThrow(() -> notFound("Không tìm thấy cửa hàng.")).getShopName();
    }

    @Transactional(readOnly = true)
    public java.util.List<ReviewPurchaseOption> eligibleOrderItems(Authentication authentication, Long productId) {
        Long userId = customers.requireCustomerId(authentication);
        requireProduct(productId);
        var eligibleItems = orderItems.findByShopOrder_Order_Customer_UserIdAndShopOrder_StatusAndVariant_Product_ProductId(
                userId, "COMPLETED", productId);
        if (eligibleItems.isEmpty()) return java.util.List.of();
        var eligibleIds = eligibleItems.stream().map(OrderItem::getOrderItemId).toList();
        var reviewedIds = reviews.findByUser_UserIdAndOrderItem_OrderItemIdIn(userId, eligibleIds).stream()
                .map(review -> review.getOrderItem().getOrderItemId()).collect(java.util.stream.Collectors.toSet());
        return eligibleItems.stream().filter(item -> !reviewedIds.contains(item.getOrderItemId()))
                .map(item -> new ReviewPurchaseOption(item.getOrderItemId(), item.getProductNameSnapshot(),
                        item.getVariantNameSnapshot(), item.getShopOrder().getOrder().getOrderCode()))
                .toList();
    }

    @Transactional(readOnly = true)
    public java.util.Set<Long> reviewedOrderItemIds(Authentication authentication, Long shopOrderId) {
        Long userId = customers.requireCustomerId(authentication);
        ShopOrder purchase = shopOrders.findByShopOrderIdAndOrderCustomerUserId(shopOrderId, userId)
                .orElseThrow(() -> notFound("Không tìm thấy đơn hàng của bạn."));
        if (!"COMPLETED".equals(purchase.getStatus())) return java.util.Set.of();
        var itemIds = orderItems.findByShopOrderId(shopOrderId).stream().map(OrderItem::getOrderItemId).toList();
        if (itemIds.isEmpty()) return java.util.Set.of();
        return reviews.findByUser_UserIdAndOrderItem_OrderItemIdIn(userId, itemIds).stream()
                .map(review -> review.getOrderItem().getOrderItemId()).collect(java.util.stream.Collectors.toSet());
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

    @Transactional(readOnly = true)
    public boolean hasCompletedShopOrder(Authentication authentication, Long shopId) {
        Long userId = customers.requireCustomerId(authentication);
        if (shopId == null || !shops.existsById(shopId)) throw notFound("Không tìm thấy cửa hàng.");
        return !shopOrders.findByOrder_Customer_UserIdAndShopIdAndStatus(userId, shopId, "COMPLETED").isEmpty();
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
        review.setStatus(ReviewStatus.APPROVED);
        reviews.save(review);
    }

    @Transactional
    public void createShopReview(Authentication authentication, Long shopId, ReviewForm form) {
        Long userId = customers.requireCustomerId(authentication);
        validate(form);
        Shop shop = shops.findById(shopId).orElseThrow(() -> notFound("Không tìm thấy cửa hàng."));
        var purchases = shopOrders.findByOrder_Customer_UserIdAndShopIdAndStatus(userId, shopId, "COMPLETED");
        if (purchases.isEmpty()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn chưa mua hàng ở cửa hàng này.");
        var purchaseIds = purchases.stream().map(ShopOrder::getShopOrderId).toList();
        var reviewedIds = reviews.findByUser_UserIdAndShopOrder_ShopOrderIdIn(userId, purchaseIds).stream()
                .map(review -> review.getShopOrder().getShopOrderId()).collect(java.util.stream.Collectors.toSet());
        ShopOrder purchase = purchases.stream().filter(item -> !reviewedIds.contains(item.getShopOrderId()))
                .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Bạn đã đánh giá các đơn hàng hoàn thành tại cửa hàng này."));
        Review review = new Review();
        review.setUser(entityManager.getReference(User.class, userId));
        review.setShop(shop);
        review.setShopOrder(purchase);
        review.setRating(form.rating().byteValue());
        review.setContent(clean(form.content()));
        review.setStatus(ReviewStatus.APPROVED);
        reviews.save(review);
    }

    @Transactional
    public void edit(Authentication authentication, Long reviewId, ReviewForm form) {
        Long userId = customers.requireCustomerId(authentication);
        validate(form);
        Review review = reviews.findOwnedReviewForUpdate(reviewId, userId)
                .orElseThrow(() -> notFound("Không tìm thấy đánh giá của bạn."));
        if (review.getStatus() != ReviewStatus.PENDING && review.getStatus() != ReviewStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Không thể chỉnh sửa đánh giá đã bị gỡ hoặc từ chối.");
        }
        review.setRating(form.rating().byteValue());
        review.setContent(clean(form.content()));
        review.setStatus(ReviewStatus.APPROVED);
    }

    @Transactional
    public void delete(Authentication authentication, Long reviewId) {
        Long userId = customers.requireCustomerId(authentication);
        Review review = reviews.findOwnedReviewForUpdate(reviewId, userId)
                .orElseThrow(() -> notFound("Không tìm thấy đánh giá của bạn."));
        if (review.getStatus() == ReviewStatus.REMOVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đánh giá đã được gỡ.");
        }
        if (reports.existsByReview_ReviewId(reviewId)) {
            review.setStatus(ReviewStatus.REMOVED);
        } else {
            reviews.delete(review);
        }
    }

    @Transactional
    public void report(Authentication authentication, Long reviewId, ReviewReportForm form) {
        Long userId = customers.requireCustomerId(authentication);
        saveReport(userId, reviewId, form, null);
    }

    @Transactional
    public void reportAsShopOwner(Authentication authentication, Long reviewId, ReviewReportForm form) {
        Long shopId = shopService.getManagedShopId(authentication);
        saveReport(auth.getCurrentUserId(authentication), reviewId, form, shopId);
    }

    @Transactional
    public void replyAsShopOwner(Authentication authentication, Long reviewId, ShopReplyForm form) {
        Long shopId = shopService.getManagedShopId(authentication);
        Long userId = auth.getCurrentUserId(authentication);
        if (form == null || form.content() == null || form.content().isBlank() || form.content().length() > 3000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nội dung phản hồi không hợp lệ.");
        }
        Review review = reviews.findByReviewIdForUpdate(reviewId)
                .orElseThrow(() -> notFound("Không tìm thấy đánh giá."));
        if (review.getStatus() != ReviewStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Chỉ có thể phản hồi đánh giá đang hiển thị.");
        }
        if (!shopId.equals(reviewShopId(review))) throw new AccessDeniedException("Đánh giá này không thuộc cửa hàng của bạn.");
        if (review.getShopReply() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đánh giá này đã có phản hồi từ cửa hàng.");
        }
        review.setShopReply(form.content().strip());
        review.setShopRepliedBy(entityManager.getReference(User.class, userId));
        review.setShopRepliedAt(LocalDateTime.now(ZoneOffset.UTC).withNano(0));
    }

    private void saveReport(Long userId, Long reviewId, ReviewReportForm form, Long reportingShopId) {
        if (form == null || form.reason() == null ||
                (form.reason() == VietFreshHub.review.dto.ReviewReportReason.OTHER &&
                        (form.description() == null || form.description().isBlank()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn lý do và mô tả lý do khác.");
        }
        Review review = reviews.findByReviewIdForUpdate(reviewId)
                .orElseThrow(() -> notFound("Không tìm thấy đánh giá."));
        if (review.getStatus() != ReviewStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Chỉ có thể báo cáo đánh giá đang hiển thị.");
        }
        if (reportingShopId != null && !reportingShopId.equals(reviewShopId(review))) {
            throw new AccessDeniedException("Đánh giá này không thuộc cửa hàng của bạn.");
        }
        if (userId.equals(review.getUser().getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không thể báo cáo đánh giá của chính mình.");
        }
        if (reports.existsByReview_ReviewIdAndReporter_UserId(reviewId, userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bạn đã báo cáo đánh giá này.");
        }
        ReviewReport report = new ReviewReport();
        report.setReview(review);
        report.setReporter(entityManager.getReference(User.class, userId));
        report.setReason(form.reason().getLabel());
        report.setDescription(clean(form.description()));
        try {
            reports.saveAndFlush(report);
        } catch (DataIntegrityViolationException duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bạn đã báo cáo đánh giá này.");
        }
    }

    @Transactional(readOnly = true)
    public Page<ReviewReportCard> pendingReports(int page) {
        return reports.findByStatusOrderByCreatedAtAsc(ReviewReportStatus.PENDING,
                PageRequest.of(Math.max(0, page), 20)).map(this::reportCard);
    }

    @Transactional
    public void moderate(Authentication authentication, Long reportId, String action) {
        ReviewReport report = reports.findByReportIdForUpdate(reportId)
                .orElseThrow(() -> notFound("Không tìm thấy báo cáo đánh giá."));
        if (report.getStatus() != ReviewReportStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Báo cáo đã được xử lý.");
        }
        Review review = reviews.findByReviewIdForUpdate(report.getReview().getReviewId())
                .orElseThrow(() -> notFound("Không tìm thấy đánh giá."));
        Long adminId = auth.getCurrentUserId(authentication);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).withNano(0);
        switch (action) {
            case "REMOVE" -> {
                if (review.getStatus() != ReviewStatus.REMOVED) {
                    review.setStatus(ReviewStatus.REMOVED);
                    review.setModeratedBy(entityManager.getReference(User.class, adminId));
                    review.setModeratedAt(now);
                    notifications.send(review.getUser().getUserId(), "REVIEW_MODERATION", "Cập nhật đánh giá",
                            "Đánh giá của bạn đã bị gỡ sau khi xem xét báo cáo.", "REVIEW", review.getReviewId());
                }
                report.setStatus(ReviewReportStatus.ACTION_TAKEN);
            }
            case "DISMISS" -> {
                report.setStatus(ReviewReportStatus.DISMISSED);
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thao tác kiểm duyệt không hợp lệ.");
        }
        report.setReviewedBy(entityManager.getReference(User.class, adminId));
        report.setReviewedAt(now);
    }

    private CatalogProduct requireProduct(Long productId) {
        return products.findById(productId).orElseThrow(() -> notFound("Không tìm thấy sản phẩm."));
    }

    private ReviewCard card(Review review) {
        return new ReviewCard(review.getReviewId(), review.getUser().getUserId(), review.getUser().getFullName(), targetName(review),
                review.getRating(), review.getContent(), review.getStatus(), review.getCreatedAt(), null,
                review.getShopReply(), review.getShopRepliedAt());
    }

    private ReviewCard shopProductCard(Review review) {
        return new ReviewCard(review.getReviewId(), review.getUser().getUserId(), review.getUser().getFullName(), targetName(review),
                review.getRating(), review.getContent(), review.getStatus(), review.getCreatedAt(),
                review.getProduct().getShop().getShopName(), review.getShopReply(), review.getShopRepliedAt());
    }

    private Long reviewShopId(Review review) {
        return review.getProduct() != null ? review.getProduct().getShop().getShopId()
                : review.getShop() != null ? review.getShop().getShopId() : null;
    }

    private String targetName(Review review) {
        return review.getProduct() != null ? review.getProduct().getName() : review.getShop().getShopName();
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
