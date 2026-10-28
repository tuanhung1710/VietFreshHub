package VietFreshHub.cart;

import VietFreshHub.auth.entity.User;
import VietFreshHub.auth.repository.UserRepository;
import VietFreshHub.cart.dto.*;
import VietFreshHub.cart.entity.*;
import VietFreshHub.cart.repository.*;
import VietFreshHub.cart.service.impl.CartServiceImpl;
import VietFreshHub.catalog.entity.*;
import VietFreshHub.catalog.repository.*;
import VietFreshHub.catalog.service.impl.CatalogServiceImpl;
import VietFreshHub.checkout.repository.CheckoutInventoryBatchRepository;
import VietFreshHub.exception.*;
import VietFreshHub.shop.entity.Shop;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.security.access.AccessDeniedException;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Plain unit tests: real catalog eligibility, mocked persistence; no Spring context. */
@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {
    @Mock CartRepository carts;
    @Mock CartItemRepository items;
    @Mock CatalogVariantRepository variants;
    @Mock CatalogProductRepository products;
    @Mock CheckoutInventoryBatchRepository stock;
    @Mock UserRepository users;
    CartServiceImpl service;
    User user;
    Cart cart;
    CatalogVariant variant;

    @BeforeEach void fixture() {
        service = new CartServiceImpl(carts, items, variants, stock, users,
                new CatalogServiceImpl(products, variants, stock));
        user = new User(); user.setUserId(7L); user.setStatus("ACTIVE");
        cart = new Cart(); cart.setCartId(70L); cart.setUser(user);
        Shop shop = new Shop(); shop.setShopId(3L); shop.setShopName("Shop");
        shop.setStatus("ACTIVE"); shop.setAvailabilityStatus("OPEN");
        CatalogProduct product = new CatalogProduct(); product.setName("Fruit");
        product.setStatus("ACTIVE"); product.setApprovalStatus("APPROVED"); product.setShop(shop);
        CatalogCategory category = new CatalogCategory(); category.setCategoryId(1L);
        product.getCategories().add(category);
        variant = new CatalogVariant(); variant.setVariantId(11L); variant.setVariantName("1 kg");
        variant.setPrice(new BigDecimal("12500.50")); variant.setProduct(product);
        product.getVariants().add(variant);
    }
    void active() { when(users.findByUserIdForUpdate(7L)).thenReturn(Optional.of(user)); }
    void existing() { when(carts.findByUserUserIdAndStatus(7L,"ACTIVE")).thenReturn(Optional.of(cart)); }
    void catalog() {
        when(variants.findById(11L)).thenReturn(Optional.of(variant));
        // Some rejection paths stop before category lookup.
        lenient().when(products.findCatalogCategories()).thenReturn(new ArrayList<>(variant.getProduct().getCategories()));
    }
    CartItem line(int quantity) {
        CartItem item = new CartItem(); item.setCartItemId(101L); item.setCart(cart);
        item.setVariant(variant); item.setQuantity(quantity); item.setUnitPriceSnapshot(new BigDecimal("10000"));
        cart.getItems().add(item); return item;
    }
    void lookup(CartItem item) { when(items.findByCartCartIdAndVariantVariantId(70L,11L)).thenReturn(Optional.of(item)); }
    AddToCartRequest request(int quantity) { return new AddToCartRequest(11L,quantity); }
    void money(String expected, BigDecimal actual) { assertEquals(0,new BigDecimal(expected).compareTo(actual)); }
    // Any inventory operation beyond the read fails verifyNoMoreInteractions, including reserve/save/delete.
    void onlyStockRead() { verify(stock).getAvailableStockByVariantId(11L); verifyNoMoreInteractions(stock); }

    @Test void addsToNewCartAtCurrentPriceWithoutInventoryMutation() {
        active(); catalog(); when(stock.getAvailableStockByVariantId(11L)).thenReturn(2);
        when(carts.save(any())).thenAnswer(i -> { Cart c=i.getArgument(0); c.setCartId(70L); return c; });
        when(items.save(any())).thenAnswer(i -> { CartItem item=i.getArgument(0); item.setCartItemId(101L); return item; });
        var response=service.addToCart(7L,request(2));
        var saved=ArgumentCaptor.forClass(CartItem.class); verify(items).save(saved.capture());
        assertSame(user,saved.getValue().getCart().getUser()); assertSame(variant,saved.getValue().getVariant());
        assertEquals(2,response.getQuantity()); assertEquals(101L,response.getCartItemId());
        assertEquals(70L,response.getCartId()); money("25001",response.getItemSubtotal());
        money("12500.50",saved.getValue().getUnitPriceSnapshot());
        assertEquals("Đã thêm sản phẩm vào giỏ hàng.",response.getMessage()); onlyStockRead();
    }
    @Test void repeatedVariantMergesIntoSameLineAtExactStockBoundary() {
        active(); existing(); catalog(); CartItem item=line(2); lookup(item);
        when(stock.getAvailableStockByVariantId(11L)).thenReturn(5);
        when(items.save(item)).thenReturn(item);
        var result=service.addToCart(7L,request(3));
        assertEquals(5,item.getQuantity()); assertEquals(101L,result.getCartItemId());
        assertEquals(1,cart.getItems().size()); money("62502.50",result.getItemSubtotal());
        verify(items).save(same(item)); verify(carts,never()).save(any()); onlyStockRead();
    }
    static Stream<AddToCartRequest> invalidAdds() {
        return Stream.of(null,new AddToCartRequest(null,1),new AddToCartRequest(0L,1),
                new AddToCartRequest(-1L,1),new AddToCartRequest(11L,null),new AddToCartRequest(11L,0),new AddToCartRequest(11L,-1));
    }
    @ParameterizedTest @MethodSource("invalidAdds") void rejectsInvalidAddBeforePersistence(AddToCartRequest request) {
        assertThrows(IllegalArgumentException.class,()->service.addToCart(7L,request));
        verifyNoInteractions(users,carts,items,variants,stock,products);
    }
    @ParameterizedTest @CsvSource({"0,1,0","2,4,5","2147483647,1,2147483647"})
    void rejectsCombinedQuantityAndIntegerOverflow(int previous,int added,int available) {
        active(); existing(); catalog(); CartItem item=line(previous); lookup(item);
        when(stock.getAvailableStockByVariantId(11L)).thenReturn(available);
        var ex=assertThrows(OutOfStockException.class,()->service.addToCart(7L,request(added)));
        assertEquals(available,ex.getAvailableStock()); assertTrue(ex.getMessage().contains(String.valueOf(available)));
        assertEquals(previous,item.getQuantity()); money("10000",item.getUnitPriceSnapshot()); verify(items,never()).save(any());
    }
    @Test void nullStockRejectsAddAsZero() {
        active(); existing(); catalog(); when(stock.getAvailableStockByVariantId(11L)).thenReturn(null);
        assertEquals(0,assertThrows(OutOfStockException.class,()->service.addToCart(7L,request(1))).getAvailableStock());
        verify(items,never()).save(any());
    }
    @ParameterizedTest @ValueSource(strings={"variant","product","approval","shop","closed","category"})
    void rejectsIneligibleCatalogOnAddAndUpdate(String reason) {
        active(); catalog();
        switch(reason) {
            case "variant" -> variant.setStatus("INACTIVE");
            case "product" -> variant.getProduct().setStatus("INACTIVE");
            case "approval" -> variant.getProduct().setApprovalStatus("PENDING");
            case "shop" -> variant.getProduct().getShop().setStatus("INACTIVE");
            case "closed" -> variant.getProduct().getShop().setAvailabilityStatus("CLOSED");
            case "category" -> variant.getProduct().getCategories().iterator().next().setStatus("INACTIVE");
        }
        assertThrows(ResourceNotFoundException.class,()->service.addToCart(7L,request(1)));
        existing(); CartItem item=line(2); lookup(item);
        assertThrows(ResourceNotFoundException.class,()->service.updateCartItemQuantity(7L,11L,3));
        assertEquals(2,item.getQuantity()); verify(items,never()).save(any()); verifyNoInteractions(stock);
    }
    @Test void missingVariantRejectsAdd() {
        active(); assertThrows(ResourceNotFoundException.class,()->service.addToCart(7L,request(1)));
        verifyNoInteractions(carts,items,stock);
    }
    @Test void updateUsesCurrentPriceAndDoesNotReserve() {
        active(); existing(); catalog(); CartItem item=line(2); lookup(item);
        when(stock.getAvailableStockByVariantId(11L)).thenReturn(4);
        service.updateCartItemQuantity(7L,11L,4);
        assertEquals(4,item.getQuantity()); money("12500.50",item.getUnitPriceSnapshot());
        verify(items).save(item); onlyStockRead();
    }
    @Test void rejectsUpdateBeyondStockWithoutChangingLine() {
        active(); existing(); catalog(); CartItem item=line(2); lookup(item);
        when(stock.getAvailableStockByVariantId(11L)).thenReturn(3);
        assertThrows(OutOfStockException.class,()->service.updateCartItemQuantity(7L,11L,4));
        assertEquals(2,item.getQuantity()); money("10000",item.getUnitPriceSnapshot()); verify(items,never()).save(any());
    }
    @Test void negativeUpdateFailsBeforePersistence() {
        assertThrows(IllegalArgumentException.class,()->service.updateCartItemQuantity(7L,11L,-1));
        verifyNoInteractions(users,carts,items,stock);
    }
    @Test void zeroRemovesEvenUnavailableItemAndPreservesOtherLine() {
        active(); existing(); CartItem item=line(2); CartItem other=line(1); lookup(item);
        variant.setStatus("INACTIVE"); service.updateCartItemQuantity(7L,11L,0);
        assertEquals(List.of(other),cart.getItems()); verify(items).delete(item); verifyNoInteractions(variants,stock);
    }
    @Test void removeDeletesOnlyLocatedLine() {
        active(); existing(); CartItem item=line(2); CartItem other=line(1); lookup(item);
        service.removeCartItem(7L,11L); assertEquals(List.of(other),cart.getItems());
        verify(items).delete(item); verifyNoInteractions(stock,variants);
    }
    @Test void clearRemovesAllLines() {
        active(); existing(); line(2); line(3); service.clearCart(7L);
        assertTrue(cart.getItems().isEmpty()); verifyNoInteractions(items,stock,variants);
    }
    @Test void absentCartIsEmptyAndRemoveClearAreIdempotent() {
        var result=service.getActiveCart(7L); assertEquals(7L,result.getUserId());
        assertTrue(result.getItems().isEmpty()); assertEquals(0,result.getTotalItems()); money("0",result.getTotalAmount());
        active(); service.removeCartItem(7L,11L); service.clearCart(7L);
        assertThrows(ResourceNotFoundException.class,()->service.updateCartItemQuantity(7L,11L,1));
        verifyNoInteractions(items,stock,variants);
    }
    @Test void existingEmptyCartAndMissingLineAreHandled() {
        active(); existing(); when(stock.getAvailableStocks(List.of())).thenReturn(Map.of());
        var result=service.getActiveCart(7L); assertTrue(result.getItems().isEmpty()); money("0",result.getTotalAmount());
        service.removeCartItem(7L,99L);
        assertThrows(ResourceNotFoundException.class,()->service.updateCartItemQuantity(7L,99L,1));
        verify(items,never()).delete(any()); verify(items,never()).save(any());
    }
    @ParameterizedTest @ValueSource(strings={"MISSING","INACTIVE","LOCKED"})
    void everyMutationRejectsUnavailableAccount(String status) {
        user.setStatus(status);
        when(users.findByUserIdForUpdate(7L)).thenReturn("MISSING".equals(status)?Optional.empty():Optional.of(user));
        assertThrows(AccessDeniedException.class,()->service.addToCart(7L,request(1)));
        assertThrows(AccessDeniedException.class,()->service.updateCartItemQuantity(7L,11L,1));
        assertThrows(AccessDeniedException.class,()->service.removeCartItem(7L,11L));
        assertThrows(AccessDeniedException.class,()->service.clearCart(7L));
        verifyNoInteractions(carts,items,variants,stock,products);
    }
    @ParameterizedTest @ValueSource(strings={"add","update","remove","clear","read"})
    void scopesLookupToSuppliedCustomerInsteadOfAnotherCart(String operation) {
        if (!operation.equals("read")) active();
        switch(operation) {
            case "add" -> { catalog(); when(stock.getAvailableStockByVariantId(11L)).thenReturn(1);
                when(carts.save(any())).thenAnswer(i->{Cart c=i.getArgument(0); c.setCartId(70L); return c;});
                when(items.save(any())).thenAnswer(i->i.getArgument(0)); service.addToCart(7L,request(1)); }
            case "update" -> assertThrows(ResourceNotFoundException.class,()->service.updateCartItemQuantity(7L,11L,1));
            case "remove" -> service.removeCartItem(7L,11L);
            case "clear" -> service.clearCart(7L);
            case "read" -> service.getActiveCart(7L);
        }
        verify(carts).findByUserUserIdAndStatus(7L,"ACTIVE");
        verify(carts,never()).findByUserUserIdAndStatus(8L,"ACTIVE");
        verify(carts,never()).findById(any()); verify(items,never()).findById(any());
    }
    @ParameterizedTest @ValueSource(strings={"15000","9000","10000.00"})
    void viewUsesCurrentPriceAndFlagsNumericPriceChange(String price) {
        existing(); line(2); variant.setPrice(new BigDecimal(price));
        when(products.findCatalogCategories()).thenReturn(new ArrayList<>(variant.getProduct().getCategories()));
        when(stock.getAvailableStocks(List.of(11L))).thenReturn(Map.of(11L,5));
        var result=service.getActiveCart(7L); var dto=result.getItems().getFirst();
        assertEquals(!price.equals("10000.00"),dto.isPriceChanged()); money("10000",dto.getPreviousPrice());
        money(price,dto.getUnitPriceSnapshot()); assertEquals(0,new BigDecimal(price).multiply(BigDecimal.TWO).compareTo(result.getTotalAmount()));
        assertTrue(dto.isPurchasable()); assertFalse(result.isHasUnavailableItems());
        money("10000",cart.getItems().getFirst().getUnitPriceSnapshot()); verify(items,never()).save(any());
    }
    @ParameterizedTest @ValueSource(strings={"empty","short","ineligible"})
    void mixedCartExcludesInvalidSubtotalButRetainsLineAndMessage(String reason) {
        existing(); CartItem valid=line(2);
        CatalogVariant unavailable=new CatalogVariant(); unavailable.setVariantId(22L); unavailable.setProduct(variant.getProduct());
        unavailable.setPrice(new BigDecimal("5000")); unavailable.setVariantName("Other");
        if(reason.equals("ineligible")) unavailable.setStatus("INACTIVE");
        CartItem invalid=line(3); invalid.setCartItemId(102L); invalid.setVariant(unavailable);
        when(products.findCatalogCategories()).thenReturn(new ArrayList<>(variant.getProduct().getCategories()));
        when(stock.getAvailableStocks(List.of(11L,22L))).thenReturn(reason.equals("empty")?Map.of(11L,5):Map.of(11L,5,22L,2));
        var result=service.getActiveCart(7L); var dto=result.getItems().get(1);
        assertEquals(5,result.getTotalItems()); money("25001",result.getTotalAmount());
        assertTrue(result.isHasUnavailableItems()); assertFalse(dto.isPurchasable()); money("15000",dto.getItemSubtotal());
        assertEquals(switch(reason) { case "empty" -> "Tạm hết hàng. Bạn có thể giữ lại trong giỏ hoặc xóa sản phẩm.";
            case "short" -> "Chỉ còn 2 sản phẩm. Vui lòng giảm số lượng."; default -> "Sản phẩm hoặc cửa hàng hiện không nhận đơn."; },dto.getAvailabilityMessage());
        assertEquals(reason.equals("short"),dto.isQuantityEditable()); assertEquals(List.of(valid,invalid),cart.getItems());
        verify(stock).getAvailableStocks(List.of(11L,22L)); verifyNoMoreInteractions(stock);
    }
    @Test void badgeUsesOnlyAggregateWithoutReadingCartCatalogOrInventory() {
        when(carts.countActiveUnits(7L)).thenReturn(8L);
        assertEquals(8L, service.countActiveUnits(7L));
        verify(carts).countActiveUnits(7L);
        verifyNoMoreInteractions(carts);
        verifyNoInteractions(items, variants, products, stock, users);
    }

}

