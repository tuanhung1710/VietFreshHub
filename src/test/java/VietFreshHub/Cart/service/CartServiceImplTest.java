package VietFreshHub.Cart.service;
import VietFreshHub.Auth.entity.User;
import VietFreshHub.Auth.repository.UserRepository;
import VietFreshHub.Cart.dto.*;
import VietFreshHub.Cart.entity.*;
import VietFreshHub.Cart.repository.*;
import VietFreshHub.Cart.service.impl.CartServiceImpl;
import VietFreshHub.Inventory.repository.InventoryBatchRepository;
import VietFreshHub.Product.entity.*;
import VietFreshHub.Product.repository.ProductVariantRepository;
import VietFreshHub.Product.service.CatalogService;
import VietFreshHub.Shop.entity.Shop;
import VietFreshHub.exception.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {
    @Mock CartRepository carts;
    @Mock CartItemRepository items;
    @Mock ProductVariantRepository variants;
    @Mock InventoryBatchRepository inventory;
    @Mock UserRepository users;
    @Mock CatalogService catalog;
    @InjectMocks CartServiceImpl service;
    User user;
    Cart cart;
    ProductVariant variant;
    @BeforeEach void setup() {
        user = new User(); user.setUserId(7L); user.setStatus("ACTIVE");
        cart = new Cart(); cart.setCartId(100L); cart.setUser(user);
        Shop shop = new Shop(); shop.setShopId(2L); shop.setShopName("Shop A");
        Product product = new Product(); product.setShop(shop); product.setName("Bưởi"); product.setSlug("buoi");
        variant = new ProductVariant(); variant.setVariantId(10L); variant.setProduct(product);
        variant.setVariantName("Hộp 1kg"); variant.setStatus("ACTIVE"); variant.setPrice(new BigDecimal("150000.00"));
    }
    private void mutation() {
        when(users.findForCartUpdate(7L)).thenReturn(Optional.of(user));
        when(carts.findByUserUserIdAndStatus(7L,"ACTIVE")).thenReturn(Optional.of(cart));
    }
    private void eligible() {
        when(variants.findById(10L)).thenReturn(Optional.of(variant));
        when(catalog.isPurchasable(variant)).thenReturn(true);
        when(inventory.getAvailableStockByVariantId(10L)).thenReturn(10);
    }
    private CartItem item(int quantity, String price) {
        CartItem item = new CartItem(); item.setCartItemId(500L); item.setCart(cart); item.setVariant(variant);
        item.setQuantity(quantity); item.setUnitPriceSnapshot(new BigDecimal(price)); cart.getItems().add(item);
        return item;
    }
    @Test void addNew() {
        mutation(); eligible();
        when(items.save(any())).thenAnswer(i -> { CartItem x=i.getArgument(0); x.setCartItemId(500L); return x; });
        var response = service.addToCart(7L,new AddToCartRequest(10L,2));
        assertEquals(2,response.getQuantity()); assertEquals(new BigDecimal("300000.00"),response.getItemSubtotal());
        var order = inOrder(users,carts); order.verify(users).findForCartUpdate(7L); order.verify(carts).findByUserUserIdAndStatus(7L,"ACTIVE");
    }
    @Test void accumulateAndRefreshPrice() {
        mutation(); eligible(); CartItem existing=item(2,"140000");
        when(items.findByCartCartIdAndVariantVariantId(100L,10L)).thenReturn(Optional.of(existing));
        when(items.save(existing)).thenReturn(existing);
        var response=service.addToCart(7L,new AddToCartRequest(10L,3));
        assertEquals(5,response.getQuantity()); assertEquals(variant.getPrice(),existing.getUnitPriceSnapshot());
    }
    @Test void excessiveQuantityNeverWrites() {
        mutation(); eligible();
        assertThrows(OutOfStockException.class,() -> service.addToCart(7L,new AddToCartRequest(10L,11)));
        verify(items,never()).save(any());
    }
    @Test void overflowCannotCreateNegativeQuantity() {
        mutation(); eligible(); CartItem existing=item(Integer.MAX_VALUE,"150000");
        when(items.findByCartCartIdAndVariantVariantId(100L,10L)).thenReturn(Optional.of(existing));
        assertThrows(OutOfStockException.class,() -> service.addToCart(7L,new AddToCartRequest(10L,1)));
        assertEquals(Integer.MAX_VALUE,existing.getQuantity()); verify(items,never()).save(any());
    }
    @Test void negativeInputRejectedBeforeDatabase() {
        assertThrows(IllegalArgumentException.class,() -> service.addToCart(7L,new AddToCartRequest(10L,-2)));
        assertThrows(IllegalArgumentException.class,() -> service.updateCartItemQuantity(7L,10L,-2));
        verifyNoInteractions(users,carts,items);
    }
    @Test void blockedUserRejected() {
        user.setStatus("BLOCKED"); when(users.findForCartUpdate(7L)).thenReturn(Optional.of(user));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,() -> service.clearCart(7L));
        verifyNoInteractions(carts,items);
    }
    @Test void unavailableProductRejected() {
        when(users.findForCartUpdate(7L)).thenReturn(Optional.of(user)); when(variants.findById(10L)).thenReturn(Optional.of(variant));
        assertThrows(ResourceNotFoundException.class,() -> service.addToCart(7L,new AddToCartRequest(10L,1)));
        verifyNoInteractions(carts,items);
    }
    @Test void readingEmptyCartDoesNotCreateAnything() {
        var response=service.getActiveCart(7L);
        assertTrue(response.getItems().isEmpty()); assertEquals(BigDecimal.ZERO,response.getTotalAmount());
        verify(carts,never()).save(any()); verifyNoInteractions(users,items);
    }
    @Test void readsCurrentPriceAndFlagsChangeWithoutWriting() {
        item(2,"140000"); when(carts.findByUserUserIdAndStatus(7L,"ACTIVE")).thenReturn(Optional.of(cart));
        when(catalog.isPurchasable(variant)).thenReturn(true); when(inventory.getAvailableStocks(anyList())).thenReturn(java.util.Map.of(10L,10));
        var response=service.getActiveCart(7L);
        assertTrue(response.getItems().getFirst().isPriceChanged()); assertEquals(new BigDecimal("300000.00"),response.getTotalAmount());
        assertEquals(new BigDecimal("140000"),cart.getItems().getFirst().getUnitPriceSnapshot());
        verify(items,never()).save(any());
    }
    @Test void shortageKeepsItemButExcludesItFromPurchasableTotal() {
        item(4,"150000"); when(carts.findByUserUserIdAndStatus(7L,"ACTIVE")).thenReturn(Optional.of(cart));
        when(catalog.isPurchasable(variant)).thenReturn(true); when(inventory.getAvailableStocks(anyList())).thenReturn(java.util.Map.of(10L,2));
        var response=service.getActiveCart(7L);
        assertEquals(1,response.getItems().size()); assertEquals(BigDecimal.ZERO,response.getTotalAmount());
        assertTrue(response.isHasUnavailableItems()); assertTrue(response.getItems().getFirst().isQuantityEditable());
    }
    @Test void updateUsesCurrentPrice() {
        mutation(); eligible(); CartItem existing=item(2,"140000");
        when(items.findByCartCartIdAndVariantVariantId(100L,10L)).thenReturn(Optional.of(existing));
        service.updateCartItemQuantity(7L,10L,3);
        assertEquals(3,existing.getQuantity()); assertEquals(variant.getPrice(),existing.getUnitPriceSnapshot());
    }
    @Test void zeroRemovesEvenUnavailableItem() {
        mutation(); CartItem existing=item(2,"140000");
        when(items.findByCartCartIdAndVariantVariantId(100L,10L)).thenReturn(Optional.of(existing));
        service.updateCartItemQuantity(7L,10L,0);
        assertTrue(cart.getItems().isEmpty()); verify(items).delete(existing); verifyNoInteractions(catalog,inventory);
    }
    @Test void clearRetainsActiveCart() {
        mutation(); item(2,"140000"); service.clearCart(7L);
        assertTrue(cart.getItems().isEmpty()); assertEquals("ACTIVE",cart.getStatus());
    }
    @Test void stockResponseDoesNotExposeUnapprovedProductsAsPurchasable() {
        when(variants.findById(10L)).thenReturn(Optional.of(variant));
        assertFalse(service.getVariantStock(10L).isPurchasable()); verifyNoInteractions(inventory);
    }
}
