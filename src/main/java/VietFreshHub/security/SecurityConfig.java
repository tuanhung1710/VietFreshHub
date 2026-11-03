package VietFreshHub.security;

import VietFreshHub.auth.repository.UserRepository;
import VietFreshHub.auth.repository.UserRoleRepository;
import VietFreshHub.auth.service.Oidc_UserService;
import VietFreshHub.auth.service.LoginRedirectService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@Configuration
@EnableMethodSecurity
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
    public RequestCache requestCache() {
        var cache = new HttpSessionRequestCache();
        cache.setMatchingRequestParameterName(null);
        cache.setRequestMatcher(request -> "GET".equals(request.getMethod())
                && !request.getServletPath().startsWith("/storefront/api/"));
        return cache;
    }
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            Oidc_UserService oidcUserService,
            SecurityContextRepository securityContextRepository,
            RequestCache requestCache,
            LoginRedirectService loginRedirects,
            UserRepository userRepository,
            UserRoleRepository userRoleRepository)
            throws Exception {
        XorCsrfTokenRequestAttributeHandler requestHandler = new XorCsrfTokenRequestAttributeHandler();
        requestHandler.setCsrfRequestAttributeName(null);

        http.csrf(csrf -> csrf.csrfTokenRequestHandler(requestHandler));

        http.securityContext(context -> {
            context.securityContextRepository(securityContextRepository);
        });
        http.requestCache(cache -> cache.requestCache(requestCache));

        http.authorizeHttpRequests(authorize -> {
            authorize.requestMatchers(org.springframework.http.HttpMethod.GET, "/home", "/customer/home")
                    .access((authentication, context) -> new AuthorizationDecision(
                            authentication.get().getAuthorities().stream()
                                    .noneMatch(authority -> "ROLE_DELIVERY_STAFF".equals(authority.getAuthority()))));
            authorize.requestMatchers(org.springframework.http.HttpMethod.GET,
                    "/product-detail", "/product-detail/**", "/shops", "/shops/**",
                    "/storefront/api/options", "/storefront/api/home", "/storefront/api/products", "/storefront/api/products/**", "/storefront/api/cart").permitAll();
            authorize.requestMatchers(org.springframework.http.HttpMethod.POST, "/storefront/api/cart").hasRole("CUSTOMER");
            authorize.requestMatchers(
                    "/", "/login", "/register", "/verify-email", "/verify-email/resend", "/error",
                    "/reviews/product/**", "/reviews/shop/**", "/payment/vnpay/return",
                    "/seller-onboarding/register", "/seller-onboarding/verify-email",
                    "/seller-onboarding/verify-email/resend", "/forgot-password", "/reset-password",
                    "/css/**", "/js/**", "/images/**","/oauth2/**", "/login/oauth2/**"
            ).permitAll()
                    .requestMatchers("/admin", "/admin/**")
                    .hasRole("ADMIN")

                    .requestMatchers(
                            "/seller", "/seller/**",
                            "/manager", "/manager/**",
                            "/store_manager", "/store_manager/**"
                    ).hasRole("STORE_MANAGER")

                    .requestMatchers("/delivery", "/delivery/**")
                    .hasRole("DELIVERY_STAFF")

                    // Applicants without a customer role may view and manage only their own applications.
                    .requestMatchers("/customer/seller-applications", "/customer/seller-applications/**")
                    .authenticated()

                    .requestMatchers(
                            "/home", "/customer", "/customer/**", "/cart", "/cart/**", "/product-detail", "/product-detail/**",
                            "/api/catalog/products/*/availability"
                    ).hasRole("CUSTOMER")

                    .anyRequest().authenticated();
        });
        http.oauth2Login(oauth2 -> oauth2
                .loginPage("/login")
                .userInfoEndpoint(userInfo -> userInfo
                        .oidcUserService(oidcUserService)
                )
                .successHandler(loginRedirects)
                .failureUrl("/login?oauthError=true")
        );

        http.formLogin(form -> {
            form.disable();
        });

        // Chuyển người chưa đăng nhập về trang login
        http.exceptionHandling(exception -> {
            exception.authenticationEntryPoint(
                    (request, response, authenticationException) -> {
                        if (request.getRequestURI().startsWith(request.getContextPath() + "/storefront/api/")) {
                            response.sendError(401);
                        } else {
                            new LoginUrlAuthenticationEntryPoint("/login").commence(request, response, authenticationException);
                        }
                    }
            );
        });

        http.logout(logout -> logout
                .logoutUrl("/logout")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID")
                .logoutSuccessUrl("/login?logout")
                .permitAll()
        );

        http.addFilterBefore(
                new AccountSessionGuardFilter(
                        userRepository,
                        userRoleRepository,
                        securityContextRepository
                ),
                AuthorizationFilter.class
        );

        return http.build();
    }
}
