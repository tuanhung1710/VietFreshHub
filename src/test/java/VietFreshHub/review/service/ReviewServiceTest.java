package VietFreshHub.review.service;

import VietFreshHub.auth.entity.User;
import VietFreshHub.auth.service.AuthService;
import VietFreshHub.auth.service.CurrentCustomerService;
import VietFreshHub.catalog.entity.CatalogProduct;
import VietFreshHub.catalog.entity.CatalogVariant;
import VietFreshHub.catalog.repository.CatalogProductRepository;
import VietFreshHub.notification.services.NotificationService;
import VietFreshHub.order.entity.Order;
import VietFreshHub.order.entity.OrderItem;
import VietFreshHub.order.entity.ShopOrder;
import VietFreshHub.order.repository.OrderItemRepository;
import VietFreshHub.order.repository.ShopOrderRepository;
import VietFreshHub.review.dto.ReviewForm;
import VietFreshHub.review.dto.ReviewReportForm;
import VietFreshHub.review.dto.ReviewReportReason;
import VietFreshHub.review.dto.ShopReplyForm;
import VietFreshHub.review.entity.Review;
import VietFreshHub.review.entity.ReviewReport;
import VietFreshHub.review.entity.ReviewReportStatus;
import VietFreshHub.review.entity.ReviewStatus;
import VietFreshHub.review.repository.ReviewReportRepository;
import VietFreshHub.review.repository.ReviewRepository;
import VietFreshHub.shop.entity.Shop;
import VietFreshHub.shop.repository.ShopRepository;
import VietFreshHub.shop.entity.Shop;
import VietFreshHub.shop.service.ShopService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReviewServiceTest {
    private final ReviewRepository reviews = mock(ReviewRepository.class);
    private final ReviewReportRepository reports = mock(ReviewReportRepository.class);
    private final OrderItemRepository orderItems = mock(OrderItemRepository.class);
    private final ShopOrderRepository shopOrders = mock(ShopOrderRepository.class);
    private final CatalogProductRepository products = mock(CatalogProductRepository.class);
    private final ShopRepository shops = mock(ShopRepository.class);
    private final CurrentCustomerService customers = mock(CurrentCustomerService.class);
    private final AuthService authService = mock(AuthService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final ShopService shopService = mock(ShopService.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final ReviewService service = new ReviewService(reviews, reports, orderItems, shopOrders, products,
            shops, customers, authService, notifications, shopService, entityManager);
    private final Authentication authentication = mock(Authentication.class);

    @Test
    void createsProductReviewAsPublicImmediately() {
        User customer = user(7L);
        CatalogProduct product = new CatalogProduct();
        product.setProductId(12L);
        CatalogVariant variant = new CatalogVariant();
        variant.setProduct(product);
        Order order = new Order();
        order.setCustomer(customer);
        ShopOrder shopOrder = new ShopOrder();
        shopOrder.setOrder(order);
        shopOrder.setStatus("COMPLETED");
        OrderItem item = new OrderItem();
        item.setShopOrder(shopOrder);
        item.setVariant(variant);

        when(customers.requireCustomerId(authentication)).thenReturn(7L);
        when(products.findById(12L)).thenReturn(Optional.of(product));
        when(orderItems.findById(31L)).thenReturn(Optional.of(item));
        when(reviews.existsByUser_UserIdAndOrderItem_OrderItemId(7L, 31L)).thenReturn(false);
        when(entityManager.getReference(User.class, 7L)).thenReturn(customer);

        service.createProductReview(authentication, 12L, 31L, new ReviewForm(5, " Fresh "));

        var saved = org.mockito.ArgumentCaptor.forClass(Review.class);
        verify(reviews).save(saved.capture());
        assertEquals(ReviewStatus.APPROVED, saved.getValue().getStatus());
        assertEquals("Fresh", saved.getValue().getContent());
    }

    @Test
    void createsShopReviewWithoutClientOrderIdUsingEligibleCompletedOrder() {
        User customer = user(7L);
        Shop shop = new Shop();
        shop.setShopId(9L);
        shop.setShopName("Fresh Shop");
        Order order = new Order();
        order.setCustomer(customer);
        ShopOrder purchase = new ShopOrder();
        purchase.setShopOrderId(51L);
        purchase.setShopId(9L);
        purchase.setOrder(order);
        purchase.setStatus("COMPLETED");
        when(customers.requireCustomerId(authentication)).thenReturn(7L);
        when(shops.findById(9L)).thenReturn(Optional.of(shop));
        when(shopOrders.findByOrder_Customer_UserIdAndShopIdAndStatus(7L, 9L, "COMPLETED"))
                .thenReturn(List.of(purchase));
        when(reviews.findByUser_UserIdAndShopOrder_ShopOrderIdIn(7L, List.of(51L))).thenReturn(List.of());
        when(entityManager.getReference(User.class, 7L)).thenReturn(customer);

        service.createShopReview(authentication, 9L, new ReviewForm(5, " Fresh "));

        var saved = org.mockito.ArgumentCaptor.forClass(Review.class);
        verify(reviews).save(saved.capture());
        assertEquals(ReviewStatus.APPROVED, saved.getValue().getStatus());
        assertEquals(shop, saved.getValue().getShop());
        assertEquals(purchase, saved.getValue().getShopOrder());
        assertEquals("Fresh", saved.getValue().getContent());
    }

    @Test
    void rejectsShopReviewWithoutCompletedPurchase() {
        Shop shop = new Shop();
        when(customers.requireCustomerId(authentication)).thenReturn(7L);
        when(shops.findById(9L)).thenReturn(Optional.of(shop));
        when(shopOrders.findByOrder_Customer_UserIdAndShopIdAndStatus(7L, 9L, "COMPLETED"))
                .thenReturn(List.of());

        var error = assertThrows(ResponseStatusException.class,
                () -> service.createShopReview(authentication, 9L, new ReviewForm(4, "Good")));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verify(reviews, never()).save(any());
    }

    @Test
    void findsReviewedItemsOnlyForOwnersOfCompletedShopOrders() {
        ShopOrder purchase = new ShopOrder();
        purchase.setShopOrderId(51L);
        purchase.setStatus("COMPLETED");
        OrderItem item = new OrderItem();
        item.setOrderItemId(31L);
        Review existing = new Review();
        existing.setOrderItem(item);
        when(customers.requireCustomerId(authentication)).thenReturn(7L);
        when(shopOrders.findByShopOrderIdAndOrderCustomerUserId(51L, 7L)).thenReturn(Optional.of(purchase));
        when(orderItems.findByShopOrderId(51L)).thenReturn(List.of(item));
        when(reviews.findByUser_UserIdAndOrderItem_OrderItemIdIn(7L, List.of(31L))).thenReturn(List.of(existing));

        assertEquals(Set.of(31L), service.reviewedOrderItemIds(authentication, 51L));
    }

    @Test
    void rejectsReportingOwnReview() {
        User owner = user(7L);
        Review review = new Review();
        review.setReviewId(20L);
        review.setUser(owner);
        review.setStatus(ReviewStatus.APPROVED);
        when(customers.requireCustomerId(authentication)).thenReturn(7L);
        when(reviews.findByReviewIdForUpdate(20L)).thenReturn(Optional.of(review));

        var error = assertThrows(ResponseStatusException.class,
                () -> service.report(authentication, 20L, new ReviewReportForm(ReviewReportReason.SPAM, null)));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verify(reports, never()).saveAndFlush(any());
    }

    @Test
    void rejectsOtherReportReasonWithoutDescription() {
        when(customers.requireCustomerId(authentication)).thenReturn(7L);

        var error = assertThrows(ResponseStatusException.class,
                () -> service.report(authentication, 20L, new ReviewReportForm(ReviewReportReason.OTHER, "  ")));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verify(reviews, never()).findByReviewIdForUpdate(any());
        verify(reports, never()).saveAndFlush(any());
    }

    @Test
    void shopOwnerCanReplyOnceToReviewForTheirShop() {
        User owner = user(7L);
        CatalogProduct product = new CatalogProduct();
        product.setShop(shop(9L));
        Review review = new Review();
        review.setReviewId(20L);
        review.setProduct(product);
        review.setUser(user(8L));
        review.setStatus(ReviewStatus.APPROVED);
        when(shopService.getManagedShopId(authentication)).thenReturn(9L);
        when(authService.getCurrentUserId(authentication)).thenReturn(7L);
        when(reviews.findByReviewIdForUpdate(20L)).thenReturn(Optional.of(review));
        when(entityManager.getReference(User.class, 7L)).thenReturn(owner);

        service.replyAsShopOwner(authentication, 20L, new ShopReplyForm(" Thank you "));

        assertEquals("Thank you", review.getShopReply());
        assertEquals(owner, review.getShopRepliedBy());
        org.junit.jupiter.api.Assertions.assertNotNull(review.getShopRepliedAt());
    }

    @Test
    void shopOwnerReportUsesAdminQueueWithoutHidingReview() {
        User owner = user(7L);
        Review review = new Review();
        review.setReviewId(20L);
        review.setProduct(productForShop(9L));
        review.setUser(user(8L));
        review.setStatus(ReviewStatus.APPROVED);
        when(shopService.getManagedShopId(authentication)).thenReturn(9L);
        when(authService.getCurrentUserId(authentication)).thenReturn(7L);
        when(reviews.findByReviewIdForUpdate(20L)).thenReturn(Optional.of(review));
        when(reports.existsByReview_ReviewIdAndReporter_UserId(20L, 7L)).thenReturn(false);
        when(entityManager.getReference(User.class, 7L)).thenReturn(owner);

        service.reportAsShopOwner(authentication, 20L, new ReviewReportForm(ReviewReportReason.SPAM, null));

        assertEquals(ReviewStatus.APPROVED, review.getStatus());
        verify(reports).saveAndFlush(any(ReviewReport.class));
    }

    @Test
    void shopOwnerCannotReplyToAnotherShopsReview() {
        Review review = new Review();
        review.setReviewId(20L);
        review.setProduct(productForShop(10L));
        review.setStatus(ReviewStatus.APPROVED);
        when(shopService.getManagedShopId(authentication)).thenReturn(9L);
        when(authService.getCurrentUserId(authentication)).thenReturn(7L);
        when(reviews.findByReviewIdForUpdate(20L)).thenReturn(Optional.of(review));

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.replyAsShopOwner(authentication, 20L, new ShopReplyForm("Hello")));
    }

    @Test
    void shopOwnerCannotCreateSecondReply() {
        Review review = new Review();
        review.setReviewId(20L);
        review.setProduct(productForShop(9L));
        review.setStatus(ReviewStatus.APPROVED);
        review.setShopReply("Existing reply");
        when(shopService.getManagedShopId(authentication)).thenReturn(9L);
        when(authService.getCurrentUserId(authentication)).thenReturn(7L);
        when(reviews.findByReviewIdForUpdate(20L)).thenReturn(Optional.of(review));

        var error = assertThrows(ResponseStatusException.class,
                () -> service.replyAsShopOwner(authentication, 20L, new ShopReplyForm("Another reply")));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertEquals("Existing reply", review.getShopReply());
    }

    @Test
    void rejectsDuplicateReportsFromSameCustomer() {
        Review review = new Review();
        review.setReviewId(20L);
        review.setUser(user(8L));
        review.setStatus(ReviewStatus.APPROVED);
        when(customers.requireCustomerId(authentication)).thenReturn(7L);
        when(reviews.findByReviewIdForUpdate(20L)).thenReturn(Optional.of(review));
        when(reports.existsByReview_ReviewIdAndReporter_UserId(20L, 7L)).thenReturn(true);

        var error = assertThrows(ResponseStatusException.class,
                () -> service.report(authentication, 20L, new ReviewReportForm(ReviewReportReason.SPAM, null)));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(reports, never()).saveAndFlush(any());
    }

    @Test
    void reportingPublishedReviewDoesNotHideIt() {
        User reporter = user(7L);
        Review review = new Review();
        review.setReviewId(20L);
        review.setUser(user(8L));
        review.setStatus(ReviewStatus.APPROVED);
        when(customers.requireCustomerId(authentication)).thenReturn(7L);
        when(reviews.findByReviewIdForUpdate(20L)).thenReturn(Optional.of(review));
        when(reports.existsByReview_ReviewIdAndReporter_UserId(20L, 7L)).thenReturn(false);
        when(entityManager.getReference(User.class, 7L)).thenReturn(reporter);

        service.report(authentication, 20L, new ReviewReportForm(ReviewReportReason.POLICY, null));

        assertEquals(ReviewStatus.APPROVED, review.getStatus());
        verify(reports).saveAndFlush(any(ReviewReport.class));
    }

    @Test
    void deletingReportedReviewKeepsItsHistoryAndHidesIt() {
        Review review = new Review();
        review.setReviewId(20L);
        review.setUser(user(7L));
        review.setStatus(ReviewStatus.APPROVED);
        when(customers.requireCustomerId(authentication)).thenReturn(7L);
        when(reviews.findOwnedReviewForUpdate(20L, 7L)).thenReturn(Optional.of(review));
        when(reports.existsByReview_ReviewId(20L)).thenReturn(true);

        service.delete(authentication, 20L);

        assertEquals(ReviewStatus.REMOVED, review.getStatus());
        verify(reviews, never()).delete(review);
    }

    @Test
    void dismissingReportKeepsReviewPublic() {
        User admin = user(2L);
        Review review = new Review();
        review.setReviewId(20L);
        review.setUser(user(8L));
        review.setStatus(ReviewStatus.APPROVED);
        ReviewReport report = new ReviewReport();
        report.setStatus(ReviewReportStatus.PENDING);
        report.setReview(review);
        when(authService.getCurrentUserId(authentication)).thenReturn(2L);
        when(reports.findByReportIdForUpdate(40L)).thenReturn(Optional.of(report));
        when(reviews.findByReviewIdForUpdate(20L)).thenReturn(Optional.of(review));
        when(entityManager.getReference(User.class, 2L)).thenReturn(admin);

        service.moderate(authentication, 40L, "DISMISS");

        assertEquals(ReviewStatus.APPROVED, review.getStatus());
        assertEquals(ReviewReportStatus.DISMISSED, report.getStatus());
        assertEquals(admin, report.getReviewedBy());
    }

    @Test
    void removingReportedReviewHidesItAndRecordsAdmin() {
        User admin = user(2L);
        Review review = new Review();
        review.setReviewId(20L);
        review.setUser(user(8L));
        review.setStatus(ReviewStatus.APPROVED);
        ReviewReport report = new ReviewReport();
        report.setStatus(ReviewReportStatus.PENDING);
        report.setReview(review);
        when(authService.getCurrentUserId(authentication)).thenReturn(2L);
        when(reports.findByReportIdForUpdate(40L)).thenReturn(Optional.of(report));
        when(reviews.findByReviewIdForUpdate(20L)).thenReturn(Optional.of(review));
        when(entityManager.getReference(User.class, 2L)).thenReturn(admin);

        service.moderate(authentication, 40L, "REMOVE");

        assertEquals(ReviewStatus.REMOVED, review.getStatus());
        assertEquals(ReviewReportStatus.ACTION_TAKEN, report.getStatus());
        assertEquals(admin, report.getReviewedBy());
        verify(notifications).send(eq(8L), any(), any(), any(), any(), eq(20L));
    }

    private User user(Long id) {
        User user = new User();
        user.setUserId(id);
        user.setFullName("Customer");
        return user;
    }

    private Shop shop(Long id) {
        Shop shop = new Shop();
        shop.setShopId(id);
        shop.setShopName("Fresh Shop");
        return shop;
    }

    private CatalogProduct productForShop(Long shopId) {
        CatalogProduct product = new CatalogProduct();
        product.setShop(shop(shopId));
        return product;
    }
}
