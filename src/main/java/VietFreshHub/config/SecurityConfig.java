package VietFreshHub.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,

            SecurityContextRepository securityContextRepository)
            throws Exception {
        CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
        requestHandler.setCsrfRequestAttributeName(null);

        http.csrf(csrf -> csrf
                .csrfTokenRequestHandler(requestHandler)
        );

        http.securityContext(context -> {
            context.securityContextRepository(securityContextRepository);
        });

        http.authorizeHttpRequests(authorize -> {
            authorize.requestMatchers(
                    "/", "/login", "/register", "/error",
                    "/css/**", "/js/**", "/images/**"
            ).permitAll()
                    .requestMatchers(org.springframework.http.HttpMethod.GET,
                            "/home", "/customer/home", "/product-detail", "/product-detail/**",
                            "/api/products/variants/*/stock").permitAll()
                    .requestMatchers("/cart", "/cart/**", "/api/carts/**", "/customer/**").hasRole("CUSTOMER")
             .requestMatchers("/admin/**").hasRole("ADMIN")
                    .requestMatchers("/manager/**").hasRole("STORE_MANAGER")
                    .requestMatchers("/store_manager/**").hasRole("STORE_MANAGER")
                    .requestMatchers("/delivery/**").hasRole("DELIVERY_STAFF")
                    .anyRequest().authenticated();
        });


        http.formLogin(form -> {
            form.disable();
        });

        // Chuyển người chưa đăng nhập về trang login
        http.exceptionHandling(exception -> {
            exception.authenticationEntryPoint(
                    (request, response, authenticationException) -> {
                        if (request.getRequestURI().startsWith(request.getContextPath() + "/api/")) {
                            response.sendError(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED);
                        } else {
                            new LoginUrlAuthenticationEntryPoint("/login").commence(request, response, authenticationException);
                        }
                    }
            );
        });

        http.logout(logout -> {
            logout.logoutUrl("/logout");
            logout.logoutSuccessUrl("/login?logout");
            logout.permitAll();
        });

        return http.build();
    }
}
