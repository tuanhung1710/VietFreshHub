package VietFreshHub.Auth;
import VietFreshHub.Auth.controller.AuthController;
import VietFreshHub.Auth.service.AuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.ui.ExtendedModelMap;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AuthLoginFlowTest {
    @AfterEach void clearSecurityContext() { SecurityContextHolder.clearContext(); }
    @Test void loginRotatesSessionAndReturnsToProductWithPersistedContext() {
        AuthService service=mock(AuthService.class);
        var auth=new UsernamePasswordAuthenticationToken("buyer@example.com",null,List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        when(service.login("buyer@example.com","secret")).thenReturn(auth);
        var controller=new AuthController(service,new HttpSessionSecurityContextRepository(),mock(PasswordEncoder.class));
        var request=new MockHttpServletRequest(); var response=new MockHttpServletResponse(); String oldId=request.getSession().getId();
        assertEquals("redirect:/product-detail/10",controller.login("buyer@example.com","secret","/product-detail/10",request,response,new ExtendedModelMap()));
        assertNotEquals(oldId,request.getSession().getId());
        assertNotNull(request.getSession().getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY));
    }
    @Test void externalReturnUrlIsIgnored() {
        AuthService service=mock(AuthService.class);
        when(service.login("buyer@example.com","secret")).thenReturn(new UsernamePasswordAuthenticationToken("buyer@example.com",null,List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
        var controller=new AuthController(service,new HttpSessionSecurityContextRepository(),mock(PasswordEncoder.class));
        assertEquals("redirect:/customer/home",controller.login("buyer@example.com","secret","https://untrusted.example",new MockHttpServletRequest(),new MockHttpServletResponse(),new ExtendedModelMap()));
    }
    @Test void failedLoginPreservesEmailAndSafeDestination() {
        var controller=new AuthController(mock(AuthService.class),new HttpSessionSecurityContextRepository(),mock(PasswordEncoder.class));
        var model=new ExtendedModelMap();
        assertEquals("auth/login",controller.login("buyer@example.com","bad","/product-detail/10",new MockHttpServletRequest(),new MockHttpServletResponse(),model));
        assertEquals("buyer@example.com",model.get("enteredEmail")); assertEquals("/product-detail/10",model.get("continuePath"));
    }
    @Test void guestRootGoesToPublicStorefront() {
        var controller=new AuthController(mock(AuthService.class),new HttpSessionSecurityContextRepository(),mock(PasswordEncoder.class));
        assertEquals("redirect:/home",controller.rootRedirect(null));
    }
    @Test void loginReturnsToProtectedCheckoutGet() {
        AuthService service=mock(AuthService.class);
        when(service.login("buyer@example.com","secret")).thenReturn(new UsernamePasswordAuthenticationToken("buyer@example.com",null,List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
        var controller=new AuthController(service,new HttpSessionSecurityContextRepository(),mock(PasswordEncoder.class));
        var request=new MockHttpServletRequest(); request.setMethod("GET"); request.setRequestURI("/customer/checkout");
        var response=new MockHttpServletResponse(); new org.springframework.security.web.savedrequest.HttpSessionRequestCache().saveRequest(request,response);
        assertEquals("redirect:/customer/checkout",controller.login("buyer@example.com","secret",null,request,response,new ExtendedModelMap()));
    }
}
