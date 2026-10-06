package VietFreshHub.Cart.controller;

import VietFreshHub.Cart.dto.AddToCartRequest;
import VietFreshHub.Cart.dto.AddToCartResponse;
import VietFreshHub.Cart.service.CartService;
import VietFreshHub.Product.dto.StockCheckResponse;
import VietFreshHub.exception.GlobalExceptionHandler;
import VietFreshHub.exception.OutOfStockException;
import VietFreshHub.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {CartController.class, GlobalExceptionHandler.class})
@AutoConfigureMockMvc(addFilters = false)
class CartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CartService cartService;

    private AddToCartRequest validRequest;
    private AddToCartResponse successResponse;

    @BeforeEach
    void setUp() {
        validRequest = new AddToCartRequest(10L, 2);

        successResponse = AddToCartResponse.builder()
                .cartItemId(500L)
                .cartId(100L)
                .variantId(10L)
                .variantName("Ri6 Durian - 1kg")
                .quantity(2)
                .unitPriceSnapshot(new BigDecimal("120000.00"))
                .itemSubtotal(new BigDecimal("240000.00"))
                .message("Added to cart.")
                .build();
    }

    @Test
    @DisplayName("POST /api/carts/items - Scenario 1 & 2: Returns HTTP 200 with MSG21")
    void testAddToCart_Success() throws Exception {
        when(cartService.addToCart(eq(1L), any(AddToCartRequest.class)))
                .thenReturn(successResponse);

        mockMvc.perform(post("/api/carts/items")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cartItemId").value(500))
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.message").value("Added to cart."));
    }

    @Test
    @DisplayName("POST /api/carts/items - Scenario 3: Insufficient stock returns HTTP 400 Bad Request")
    void testAddToCart_InsufficientStock_ReturnsHttp400() throws Exception {
        when(cartService.addToCart(eq(1L), any(AddToCartRequest.class)))
                .thenThrow(new OutOfStockException("Only 3 items left in stock", 3));

        mockMvc.perform(post("/api/carts/items")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.message").value("Only 3 items left in stock"));
    }

    @Test
    @DisplayName("POST /api/carts/items - Scenario 4: Inactive Variant returns HTTP 400 Bad Request")
    void testAddToCart_InactiveVariant_ReturnsHttp400() throws Exception {
        when(cartService.addToCart(eq(1L), any(AddToCartRequest.class)))
                .thenThrow(new ResourceNotFoundException("Variant is no longer available."));

        mockMvc.perform(post("/api/carts/items")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Variant is no longer available."));
    }

    @Test
    @DisplayName("GET /api/products/variants/{variantId}/stock - Scenario 5: Returns stock pre-validation info")
    void testGetVariantStock_Scenario5() throws Exception {
        StockCheckResponse stockResponse = StockCheckResponse.builder()
                .variantId(10L)
                .availableStock(15)
                .status("ACTIVE")
                .purchasable(true)
                .build();

        when(cartService.getVariantStock(10L)).thenReturn(stockResponse);

        mockMvc.perform(get("/api/products/variants/10/stock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variantId").value(10))
                .andExpect(jsonPath("$.availableStock").value(15))
                .andExpect(jsonPath("$.purchasable").value(true));
    }
}
