package com.example.recruitmentsystem.config;

import com.example.recruitmentsystem.security.AppUserDetailsService;
import com.example.recruitmentsystem.security.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Replaces the original authentication scheme, which read the caller's identity from an
 * {@code X-Username} request header. That header is entirely under the client's control, so
 * every endpoint was reachable as any user, including the administrator.
 *
 * <p>Identity now comes from a server-side session established by a real authentication step.
 * Because the session is carried in a cookie, and cookies are attached by the browser to
 * cross-site requests automatically, CSRF protection is required and is enabled here.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * Wired explicitly so the login endpoint can authenticate through the same provider chain the
     * filter chain uses, rather than comparing password hashes by hand as the original code did.
     */
    @Bean
    public AuthenticationManager authenticationManager(AppUserDetailsService userDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        // Without this the provider reports "bad credentials" for a locked account, which would
        // make the lockout invisible to the caller and to the audit log.
        provider.setHideUserNotFoundExceptions(true);
        return new ProviderManager(provider);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   RestAuthenticationEntryPoint entryPoint) throws Exception {
        // The token is readable by the page's own scripts so a fetch() can echo it back, which
        // is what makes the double-submit check work for this front end. It stays unreadable to
        // any other origin, which is what makes it a defence.
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();

        return http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        // A new session id is issued on login, so a session id an attacker
                        // planted before authentication is discarded rather than upgraded.
                        .sessionFixation(fixation -> fixation.changeSessionId())
                        .maximumSessions(3))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .contentTypeOptions(Customizer.withDefaults())
                        .referrerPolicy(referrer ->
                                referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31_536_000))
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; script-src 'self'; style-src 'self'; "
                                        + "img-src 'self' data:; frame-ancestors 'none'; "
                                        + "form-action 'self'; base-uri 'self'")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login", "/api/auth/register").permitAll()
                        .requestMatchers("/api/auth/csrf").permitAll()
                        // Browsing vacancies is public; posting one is not. The method matters,
                        // so this is scoped to GET rather than to the path.
                        .requestMatchers(HttpMethod.GET, "/api/jobs").permitAll()
                        .requestMatchers("/", "/index.html", "/login.html", "/register.html",
                                "/jobseeker-login.html", "/employer-login.html",
                                "/register-applicant.html", "/faq.html",
                                "/css/**", "/js/**", "/favicon.ico").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling.authenticationEntryPoint(entryPoint))
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.setStatus(204))
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID"))
                .build();
    }
}
