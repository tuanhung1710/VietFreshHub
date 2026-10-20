package VietFreshHub.payment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/** Narrow public callback chain; does not change TV1's login/customer filter chain. */
@Configuration
public class VnpayCallbackSecurity {
    @Bean @Order(0)
    SecurityFilterChain vnpayCallbacks(HttpSecurity http) throws Exception {
        return http.securityMatcher("/payments/vnpay/ipn", "/payments/vnpay/return")
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.requestMatchers(HttpMethod.GET,
                        "/payments/vnpay/ipn", "/payments/vnpay/return").permitAll().anyRequest().denyAll())
                .build();
    }
}
