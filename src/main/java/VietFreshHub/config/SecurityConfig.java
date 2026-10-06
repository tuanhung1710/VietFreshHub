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

        http.csrf(csrf -> csrf.csrfTokenRequestHandler(requestHandler));

        http.securityContext(context -> {
            context.securityContextRepository(securityContextRepository);
        });

        http.authorizeHttpRequests(authorize -> {
            authorize.requestMatchers(
                    "/", "/login", "/register", "/verify-email",
                            "/verify-email/resend", "/error",
                    "/css/**", "/js/**", "/images/**"
            ).permitAll()
             .requestMatchers("/admin/**").hasRole("ADMIN")
                    .requestMatchers("/manager/**").hasRole("STORE_MANAGER")
                    .requestMatchers("/seller", "/seller/**").hasRole("STORE_MANAGER")
                    .requestMatchers("/delivery", "/delivery/**").hasRole("DELIVERY_STAFF")
                    .requestMatchers("/customer/**").hasRole("CUSTOMER")
                    .anyRequest().authenticated();
        });


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

        return http.build();
    }
}
