package VietFreshHub.Cart.service;

import VietFreshHub.Auth.entity.User;
import VietFreshHub.Auth.repository.UserRepository;
import VietFreshHub.Cart.dto.AddToCartRequest;
import VietFreshHub.Cart.dto.AddToCartResponse;
import VietFreshHub.Cart.entity.Cart;
import VietFreshHub.Cart.entity.CartItem;
import VietFreshHub.Cart.repository.CartItemRepository;
import VietFreshHub.Cart.repository.CartRepository;
import VietFreshHub.Cart.service.impl.CartServiceImpl;
import VietFreshHub.Inventory.repository.InventoryBatchRepository;
import VietFreshHub.Product.dto.StockCheckResponse;
import VietFreshHub.Product.entity.ProductVariant;
import VietFreshHub.Product.repository.ProductVariantRepository;
import VietFreshHub.exception.OutOfStockException;
import VietFreshHub.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private InventoryBatchRepository inventoryBatchRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CartServiceImpl cartService;

    private User sampleUser;
    private Cart sampleCart;
    private ProductVariant sampleVariant;

    @BeforeEach
    void setUp() {
        sampleUser = new User();
        sampleUser.setUserId(1L);
        sampleUser.setEmail("customer@vietfreshhub.com");

        sampleCart = new Cart();
        sampleCart.setCartId(100L);
        sampleCart.setUser(sampleUser);
        sampleCart.setStatus("ACTIVE");

        sampleVariant = new ProductVariant();
        sampleVariant.setVariantId(10L);
        sampleVariant.setVariantName("Thieu Longan - Grade A (1kg)");
        sampleVariant.setPrice(new BigDecimal("150000.00"));
        sampleVariant.setStatus("ACTIVE");
    }

    @Test
    @DisplayName("Scenario 1: Happy Path - Add New Variant to Cart successfully")
    void testScenario1_AddNewVariantToCart_Success() {
        // Arrange
        AddToCartRequest request = new AddToCartRequest(10L, 2);

        when(productVariantRepository.findById(10L)).thenReturn(Optional.of(sampleVariant));
        when(cartRepository.findByUserUserIdAndStatus(1L, "ACTIVE")).thenReturn(Optional.of(sampleCart));
        when(inventoryBatchRepository.getAvailableStockByVariantId(10L)).thenReturn(10);
        when(cartItemRepository.findByCartCartIdAndVariantVariantId(100L, 10L)).thenReturn(Optional.empty());

        when(cartItemRepository.save(any(CartItem.class))).thenAnswer(invocation -> {
            CartItem item = invocation.getArgument(0);
            item.setCartItemId(500L);
            return item;
        });

        // Act
        AddToCartResponse response = cartService.addToCart(1L, request);

        // Assert
        assertNotNull(response);
        assertEquals(500L, response.getCartItemId());
        assertEquals(100L, response.getCartId());
        assertEquals(10L, response.getVariantId());
        assertEquals(2, response.getQuantity());
        assertEquals(new BigDecimal("150000.00"), response.getUnitPriceSnapshot());
        assertEquals(new BigDecimal("300000.00"), response.getItemSubtotal());
        assertEquals("Added to cart.", response.getMessage());

        ArgumentCaptor<CartItem> itemCaptor = ArgumentCaptor.forClass(CartItem.class);
        verify(cartItemRepository).save(itemCaptor.capture());
        CartItem saved = itemCaptor.getValue();
        assertEquals(2, saved.getQuantity());
        assertEquals(new BigDecimal("150000.00"), saved.getUnitPriceSnapshot());
    }

    @Test
    @DisplayName("Scenario 2: Happy Path - Add Existing Variant (Quantity Accumulation & Price Snapshot Update)")
    void testScenario2_AddExistingVariant_QuantityAccumulation_Success() {
        // Arrange
        AddToCartRequest request = new AddToCartRequest(10L, 3);

        CartItem existingItem = new CartItem();
        existingItem.setCartItemId(500L);
        existingItem.setCart(sampleCart);
        existingItem.setVariant(sampleVariant);
        existingItem.setQuantity(2); // Existing quantity
        existingItem.setUnitPriceSnapshot(new BigDecimal("140000.00")); // Old price snapshot

        // Updated price on variant
        sampleVariant.setPrice(new BigDecimal("150000.00"));

        when(productVariantRepository.findById(10L)).thenReturn(Optional.of(sampleVariant));
        when(cartRepository.findByUserUserIdAndStatus(1L, "ACTIVE")).thenReturn(Optional.of(sampleCart));
        when(inventoryBatchRepository.getAvailableStockByVariantId(10L)).thenReturn(10); // 10 available stock
        when(cartItemRepository.findByCartCartIdAndVariantVariantId(100L, 10L)).thenReturn(Optional.of(existingItem));
        when(cartItemRepository.save(any(CartItem.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        AddToCartResponse response = cartService.addToCart(1L, request);

        // Assert: New accumulated quantity = 2 + 3 = 5
        assertNotNull(response);
        assertEquals(5, response.getQuantity());
        assertEquals(new BigDecimal("150000.00"), response.getUnitPriceSnapshot()); // Price updated to current
        assertEquals(new BigDecimal("750000.00"), response.getItemSubtotal());
        assertEquals("Added to cart.", response.getMessage());

        verify(cartItemRepository).save(existingItem);
        assertEquals(5, existingItem.getQuantity());
        assertEquals(new BigDecimal("150000.00"), existingItem.getUnitPriceSnapshot());
    }

    @Test
    @DisplayName("Scenario 3: Edge Case - Insufficient Stock (Throws OutOfStockException)")
    void testScenario3_InsufficientStock_ThrowsOutOfStockException() {
        // Arrange: User requests quantity = 5, but available stock is only 3
        AddToCartRequest request = new AddToCartRequest(10L, 5);

        when(productVariantRepository.findById(10L)).thenReturn(Optional.of(sampleVariant));
        when(cartRepository.findByUserUserIdAndStatus(1L, "ACTIVE")).thenReturn(Optional.of(sampleCart));
        when(inventoryBatchRepository.getAvailableStockByVariantId(10L)).thenReturn(3); // Only 3 left
        when(cartItemRepository.findByCartCartIdAndVariantVariantId(100L, 10L)).thenReturn(Optional.empty());

        // Act & Assert
        OutOfStockException exception = assertThrows(OutOfStockException.class, () -> {
            cartService.addToCart(1L, request);
        });

        assertEquals("Only 3 items left in stock", exception.getMessage());
        assertEquals(3, exception.getAvailableStock());

        // Verify DB was NOT modified
        verify(cartItemRepository, never()).save(any());
    }

    @Test
    @DisplayName("Scenario 4: Edge Case - Variant Inactive or Deleted (Throws ResourceNotFoundException)")
    void testScenario4_InactiveVariant_ThrowsResourceNotFoundException() {
        // Arrange
        sampleVariant.setStatus("INACTIVE");
        AddToCartRequest request = new AddToCartRequest(10L, 1);

        when(productVariantRepository.findById(10L)).thenReturn(Optional.of(sampleVariant));

        // Act & Assert
        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> {
            cartService.addToCart(1L, request);
        });

        assertEquals("Variant is no longer available.", exception.getMessage());

        // Verify no cart lookup or DB operations performed
        verify(cartRepository, never()).findByUserUserIdAndStatus(anyLong(), anyString());
        verify(cartItemRepository, never()).save(any());
    }

    @Test
    @DisplayName("Scenario 5: UI/UX Frontend Rendering - Real-time Stock Pre-validation Check")
    void testScenario5_GetVariantStock_PreValidation() {
        // Case 5a: Active variant with stock > 0
        when(productVariantRepository.findById(10L)).thenReturn(Optional.of(sampleVariant));
        when(inventoryBatchRepository.getAvailableStockByVariantId(10L)).thenReturn(8);

        StockCheckResponse activeResponse = cartService.getVariantStock(10L);
        assertTrue(activeResponse.isPurchasable());
        assertEquals(8, activeResponse.getAvailableStock());
        assertEquals("ACTIVE", activeResponse.getStatus());

        // Case 5b: Out of stock variant
        when(inventoryBatchRepository.getAvailableStockByVariantId(10L)).thenReturn(0);
        StockCheckResponse outOfStockResponse = cartService.getVariantStock(10L);
        assertFalse(outOfStockResponse.isPurchasable());
        assertEquals(0, outOfStockResponse.getAvailableStock());
    }
}
