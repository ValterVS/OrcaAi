package com.orcaai.shared.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Session-based authentication for a same-origin SPA.
 *
 * <p>The session lives in PostgreSQL (Spring Session JDBC) and is referenced by an HttpOnly cookie.
 * Because the browser sends that cookie automatically, every state-changing request requires the
 * CSRF token from the {@code XSRF-TOKEN} cookie echoed in the {@code X-XSRF-TOKEN} header.
 */
@Configuration
@EnableMethodSecurity
class SecurityConfig {

    static final String LOGIN_PATH = "/api/auth/login";
    static final String LOGOUT_PATH = "/api/auth/logout";
    static final String SIGNUP_PATH = "/api/auth/signup";
    static final String RESEND_VERIFICATION_PATH = "/api/auth/resend-verification";
    static final String FORGOT_PASSWORD_PATH = "/api/auth/forgot-password";
    static final String VERIFY_EMAIL_PATH = "/api/auth/verify-email";
    static final String RESET_PASSWORD_PATH = "/api/auth/reset-password";
    static final String ACCEPT_INVITATION_PATH = "/api/invitations/accept";

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityProblemHandler problemHandler,
            AuthRateLimitProperties rateLimitProperties,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, SIGNUP_PATH, RESEND_VERIFICATION_PATH, FORGOT_PASSWORD_PATH,
                                VERIFY_EMAIL_PATH, RESET_PASSWORD_PATH, ACCEPT_INVITATION_PATH).permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf.spa())
                .formLogin(form -> form
                        // Setting loginPage disables the generated HTML login page.
                        .loginPage(LOGIN_PATH)
                        .loginProcessingUrl(LOGIN_PATH)
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .successHandler((request, response, authentication) ->
                                response.setStatus(HttpStatus.NO_CONTENT.value()))
                        .failureHandler(problemHandler)
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl(LOGOUT_PATH)
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'")))
                .addFilterBefore(
                        new AuthRateLimitFilter(rateLimitProperties, resolver),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Account status (disabled, unverified) is checked only after the password, so those accounts take
     * the same time and get the same response as a wrong password: login cannot reveal account state.
     */
    @Bean
    DaoAuthenticationProvider authenticationProvider(UserDetailsService users, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(passwordEncoder);
        provider.setPreAuthenticationChecks(user -> {
        });
        provider.setPostAuthenticationChecks(new AccountStatusUserDetailsChecker());
        return provider;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
