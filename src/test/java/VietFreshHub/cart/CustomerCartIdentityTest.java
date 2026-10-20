package VietFreshHub.cart;

import VietFreshHub.auth.service.CurrentCustomerService;
import VietFreshHub.cart.controller.CustomerCartViewController;
import VietFreshHub.cart.dto.*;
import VietFreshHub.cart.service.CartService;
import VietFreshHub.catalog.service.CatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Direct controller calls verify identity forwarding only, not filters/CSRF/session integration. */
class CustomerCartIdentityTest {
    @Test void forwardsResolvedCustomerOnEveryMutation() {
        CartService cart=mock(CartService.class);
        CurrentCustomerService current=mock(CurrentCustomerService.class);
        Authentication auth=mock(Authentication.class);
        when(current.requireCustomerId(auth)).thenReturn(7L);
        var controller=new CustomerCartViewController(cart,mock(CatalogService.class),current);
        var errors=mock(BindingResult.class);
        var flash=new RedirectAttributesModelMap();
        var add=new AddToCartRequest(11L,2);
        var update=new UpdateCartQuantityRequest(); update.setVariantId(11L); update.setQuantity(3);
        assertEquals("redirect:/cart",controller.handleAddToCart(add,errors,auth,null,flash));
        assertEquals("redirect:/cart",controller.handleUpdateQuantity(update,errors,auth,flash));
        controller.handleRemoveItem(11L,auth,flash); controller.handleClearCart(auth,flash);
        verify(cart).addToCart(7L,add); verify(cart).updateCartItemQuantity(7L,11L,3);
        verify(cart).removeCartItem(7L,11L); verify(cart).clearCart(7L); verifyNoMoreInteractions(cart);
    }
    @Test void authenticationFailureNeverReachesCartMutation() {
        CartService cart=mock(CartService.class);
        CurrentCustomerService current=mock(CurrentCustomerService.class);
        when(current.requireCustomerId(null)).thenThrow(new AccessDeniedException("Denied"));
        var controller=new CustomerCartViewController(cart,mock(CatalogService.class),current);
        var errors=mock(BindingResult.class); var flash=new RedirectAttributesModelMap();
        var update=new UpdateCartQuantityRequest(); update.setVariantId(11L); update.setQuantity(1);
        assertThrows(AccessDeniedException.class,()->controller.handleAddToCart(new AddToCartRequest(11L,1),errors,null,null,flash));
        assertThrows(AccessDeniedException.class,()->controller.handleUpdateQuantity(update,errors,null,flash));
        assertThrows(AccessDeniedException.class,()->controller.handleRemoveItem(11L,null,flash));
        assertThrows(AccessDeniedException.class,()->controller.handleClearCart(null,flash)); verifyNoInteractions(cart);
    }
}
