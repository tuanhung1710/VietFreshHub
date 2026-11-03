package VietFreshHub.order;

import VietFreshHub.auth.entity.User;
import VietFreshHub.auth.repository.UserRepository;
import VietFreshHub.cart.repository.*;
import VietFreshHub.order.entity.*;
import VietFreshHub.order.entity.Order;
import VietFreshHub.order.repository.*;
import VietFreshHub.order.service.ShopCustomerOrderServiceImpl;
import VietFreshHub.order.service.impl.CustomerOrderServiceImpl;
import VietFreshHub.exception.CustomerOrderException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class ReorderEligibilityTest {
    @Mock UserRepository users;
    @Mock OrderRepository orders;
    @Mock ShopOrderRepository shops;
    @Mock CartRepository carts;
    @Mock CartItemRepository items;
    @Mock EntityManager em;
    @InjectMocks ShopCustomerOrderServiceImpl shopService;
    @InjectMocks CustomerOrderServiceImpl receiptService;
    AutoCloseable mocks;
    @BeforeEach void setup() {
        mocks=MockitoAnnotations.openMocks(this);
        var user=new User();user.setStatus("ACTIVE");
        when(users.findByUserIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(orders.findByOrderIdAndCustomerUserId(9L,7L)).thenReturn(Optional.of(new Order()));
    }
    @AfterEach void close() throws Exception {mocks.close();}
    ShopOrder shop(String status){var shop=new ShopOrder();shop.setStatus(status);return shop;}
    @ParameterizedTest @ValueSource(strings={"PENDING","CONFIRMED","PREPARING","READY_FOR_DELIVERY","OUT_FOR_DELIVERY","COMPLETED"})
    void activeAndCompletedOrdersCannotMutateCart(String status) {
        var shop=shop(status);
        when(shops.findByShopOrderIdAndOrderCustomerUserId(10L,7L)).thenReturn(Optional.of(shop));
        when(shops.findByOrderOrderIdOrderByShopOrderIdAsc(9L)).thenReturn(List.of(shop));
        assertThrows(CustomerOrderException.class,()->shopService.reorderShop(7L,10L));
        assertThrows(CustomerOrderException.class,()->receiptService.reorder(7L,9L));
        verifyNoInteractions(carts,items);
    }
    @Test void mixedReceiptCannotReorderActiveShop() {
        when(shops.findByOrderOrderIdOrderByShopOrderIdAsc(9L)).thenReturn(List.of(shop("CANCELLED"),shop("PENDING")));
        assertThrows(CustomerOrderException.class,()->receiptService.reorder(7L,9L));
        verifyNoInteractions(carts,items);
    }
    @Test void cancelledOrdersReachExistingCartLogic() {
        var shop=shop("CANCELLED");
        when(shops.findByShopOrderIdAndOrderCustomerUserId(10L,7L)).thenReturn(Optional.of(shop));
        when(shops.findByOrderOrderIdOrderByShopOrderIdAsc(9L)).thenReturn(List.of(shop));
        assertDoesNotThrow(()->shopService.reorderShop(7L,10L));
        assertDoesNotThrow(()->receiptService.reorder(7L,9L));
        verify(carts,times(2)).findByUserUserIdAndStatus(7L,"ACTIVE");
    }
}
