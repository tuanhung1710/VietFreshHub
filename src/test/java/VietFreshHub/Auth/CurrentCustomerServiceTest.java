package VietFreshHub.Auth;
import VietFreshHub.Auth.entity.User;
import VietFreshHub.Auth.repository.UserRepository;
import VietFreshHub.Auth.service.CurrentCustomerService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.access.AccessDeniedException;
import java.util.List;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class CurrentCustomerServiceTest {
    @Test void resolvesPrincipalInsteadOfClientId() {
        UserRepository repo=mock(UserRepository.class); User user=new User(); user.setUserId(42L); user.setStatus("ACTIVE");
        when(repo.findByEmailIgnoreCase("buyer@example.com")).thenReturn(Optional.of(user));
        var auth=new UsernamePasswordAuthenticationToken("buyer@example.com",null,List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        assertEquals(42L,new CurrentCustomerService(repo).requireCustomerId(auth));
        user.setStatus("BLOCKED"); assertThrows(AccessDeniedException.class,() -> new CurrentCustomerService(repo).requireCustomerId(auth));
    }
    @Test void anonymousAndOtherRolesRejectedBeforeLookup() {
        UserRepository repo=mock(UserRepository.class); var service=new CurrentCustomerService(repo);
        assertThrows(AccessDeniedException.class,() -> service.requireCustomerId(null));
        var auth=new UsernamePasswordAuthenticationToken("admin",null,List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        assertThrows(AccessDeniedException.class,() -> service.requireCustomerId(auth)); verifyNoInteractions(repo);
    }
}
