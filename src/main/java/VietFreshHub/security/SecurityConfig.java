package VietFreshHub.security;

import VietFreshHub.auth.repository.UserRepository;
import VietFreshHub.auth.repository.UserRoleRepository;
import VietFreshHub.auth.service.Oidc_UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;

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
            Oidc_UserService oidcUserService,
            SecurityContextRepository securityContextRepository,
            UserRepository userRepository,
            UserRoleRepository userRoleRepository)
            throws Exception {
        XorCsrfTokenRequestAttributeHandler requestHandler = new XorCsrfTokenRequestAttributeHandler();
        requestHandler.setCsrfRequestAttributeName(null);

        http.csrf(csrf -> csrf.csrfTokenRequestHandler(requestHandler));

        http.securityContext(context -> {
            context.securityContextRepository(securityContextRepository);
        });

        http.authorizeHttpRequests(authorize -> {
            authorize.requestMatchers(
                    "/", "/login", "/register", "/verify-email",
                            "/verify-email/resend", "/error",
                    "/css/**", "/js/**", "/images/**","/oauth2/**", "/login/oauth2/**"
            ).permitAll()
             .requestMatchers("/admin/**").hasRole("ADMIN")
                    .requestMatchers("/store_manager/**").hasRole("STORE_MANAGER")
                    .requestMatchers("/delivery/**").hasRole("DELIVERY_STAFF")
                    .requestMatchers("/customer/**").hasRole("CUSTOMER")
                    .anyRequest().authenticated();
        });
        http.oauth2Login(oauth2 -> oauth2
                .loginPage("/login")
                .userInfoEndpoint(userInfo -> userInfo
                        .oidcUserService(oidcUserService)
                )
                .defaultSuccessUrl("/customer/home", true)
                .failureUrl("/login?oauthError=true")
        );

        http.formLogin(form -> {
            form.disable();
        });

        // Chuyển người chưa đăng nhập về trang login
        http.exceptionHandling(exception -> {
            exception.authenticationEntryPoint(
                    new LoginUrlAuthenticationEntryPoint("/login")
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
