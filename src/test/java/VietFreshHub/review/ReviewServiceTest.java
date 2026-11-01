package VietFreshHub.review;

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
import VietFreshHub.review.entity.Review;
import VietFreshHub.review.entity.ReviewStatus;
import VietFreshHub.review.repository.ReviewReportRepository;
import VietFreshHub.review.repository.ReviewRepository;
import VietFreshHub.review.service.ReviewService;
import VietFreshHub.shop.repository.ShopRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReviewServiceTest {
    @Test
    void productReviewRequiresCompletedPurchaseByCurrentCustomerAndStoresCorrectLinks() {
        var f = fixture(10L);
        when(f.customers.requireCustomerId(f.authentication)).thenReturn(10L);
        when(f.products.findById(5L)).thenReturn(Optional.of(f.product));
        when(f.orderItems.findById(50L)).thenReturn(Optional.of(f.item));
        when(f.reviews.existsByUser_UserIdAndOrderItem_OrderItemId(10L, 50L)).thenReturn(false);
        when(f.entityManager.getReference(User.class, 10L)).thenReturn(f.user);

        f.service.createProductReview(f.authentication, 5L, 50L, new ReviewForm(5, "Great"));

        var saved = org.mockito.ArgumentCaptor.forClass(Review.class);
        verify(f.reviews).save(saved.capture());
        assertEquals(ReviewStatus.PENDING, saved.getValue().getStatus());
        assertEquals(Byte.valueOf((byte) 5), saved.getValue().getRating());
        assertEquals(5L, saved.getValue().getProduct().getProductId());
        assertEquals(50L, saved.getValue().getOrderItem().getOrderItemId());
        assertEquals(null, saved.getValue().getShop());
        verify(f.reviews).existsByUser_UserIdAndOrderItem_OrderItemId(10L, 50L);
    }

    @Test
    void anotherCustomersOrderCannotBeReviewed() {
        var f = fixture(11L);
        when(f.customers.requireCustomerId(f.authentication)).thenReturn(10L);
        when(f.products.findById(5L)).thenReturn(Optional.of(f.product));
        when(f.orderItems.findById(50L)).thenReturn(Optional.of(f.item));

        var error = assertThrows(ResponseStatusException.class,
                () -> f.service.createProductReview(f.authentication, 5L, 50L, new ReviewForm(4, null)));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verify(f.reviews, never()).save(any());
    }

    @Test
    void reviewRatingOutsideSchemaConstraintIsRejected() {
        var f = fixture(10L);
        when(f.customers.requireCustomerId(f.authentication)).thenReturn(10L);

        var error = assertThrows(ResponseStatusException.class,
                () -> f.service.createProductReview(f.authentication, 5L, 50L, new ReviewForm(6, null)));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verify(f.orderItems, never()).findById(any());
    }

    @Test
    void adminModerationUpdatesReviewAndNotifiesItsAuthorInWeb() {
        var f = fixture(10L);
        User author = new User(); author.setUserId(10L);
        User moderator = new User(); moderator.setUserId(99L);
        Review review = new Review(); review.setReviewId(70L); review.setUser(author);
        when(f.auth.getCurrentUserId(f.authentication)).thenReturn(99L);
        when(f.reviews.findById(70L)).thenReturn(Optional.of(review));
        when(f.entityManager.getReference(User.class, 99L)).thenReturn(moderator);

        f.service.moderateReview(f.authentication, 70L, "APPROVE");

        assertEquals(ReviewStatus.APPROVED, review.getStatus());
        assertEquals(moderator, review.getModeratedBy());
        verify(f.notifications).send(10L, "REVIEW_MODERATION", "Cập nhật đánh giá",
                "Đánh giá của bạn đã được duyệt.", "REVIEW", 70L);
    }

    private Fixture fixture(long orderOwnerId) {
        ReviewRepository reviews = mock(ReviewRepository.class);
        ReviewReportRepository reports = mock(ReviewReportRepository.class);
        OrderItemRepository orderItems = mock(OrderItemRepository.class);
        ShopOrderRepository shopOrders = mock(ShopOrderRepository.class);
        CatalogProductRepository products = mock(CatalogProductRepository.class);
        ShopRepository shops = mock(ShopRepository.class);
        CurrentCustomerService customers = mock(CurrentCustomerService.class);
        AuthService auth = mock(AuthService.class);
        NotificationService notifications = mock(NotificationService.class);
        EntityManager entityManager = mock(EntityManager.class);
        Authentication authentication = mock(Authentication.class);
        User user = new User(); user.setUserId(10L);
        User orderOwner = new User(); orderOwner.setUserId(orderOwnerId);
        CatalogProduct product = new CatalogProduct(); product.setProductId(5L);
        CatalogVariant variant = new CatalogVariant(); variant.setVariantId(7L); variant.setProduct(product);
        Order order = new Order(); order.setCustomer(orderOwner);
        ShopOrder shopOrder = new ShopOrder(); shopOrder.setShopOrderId(60L); shopOrder.setStatus("COMPLETED");
        shopOrder.setOrder(order);
        OrderItem item = new OrderItem(); item.setOrderItemId(50L); item.setVariant(variant); item.setShopOrder(shopOrder);
        ReviewService service = new ReviewService(reviews, reports, orderItems, shopOrders, products, shops,
                customers, auth, notifications, entityManager);
        return new Fixture(service, reviews, orderItems, products, customers, entityManager, authentication,
                product, item, user, auth, notifications);
    }

    private record Fixture(ReviewService service, ReviewRepository reviews, OrderItemRepository orderItems,
                          CatalogProductRepository products, CurrentCustomerService customers,
                          EntityManager entityManager, Authentication authentication, CatalogProduct product,
                          OrderItem item, User user, AuthService auth, NotificationService notifications) {}
}
