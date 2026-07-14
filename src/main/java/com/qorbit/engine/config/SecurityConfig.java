package com.qorbit.engine.config;

import com.qorbit.engine.auth.service.QorbitAuthSuccessHandler;
import com.qorbit.engine.auth.service.QorbitUserDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.session.HttpSessionEventPublisher;

@Configuration
@EnableWebSecurity
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private QorbitUserDetailsService userDetailsService;

    @Autowired
    private QorbitAuthSuccessHandler authSuccessHandler;

    @Value("${qorbit.remember-me.key}")
    private String rememberMeKey;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // ── Autorização ───────────────────────────────────────────────────
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/auth/login", "/auth/register", "/auth/logout",
                    "/auth/forgot-password", "/auth/reset-password",
                    "/auth/verify-email",
                    "/css/**", "/js/**", "/images/**", "/favicon.ico",
                    "/qorbit-manual-usuario.html",
                    "/api/diagnostico/**", "/error"
                ).permitAll()
                .requestMatchers("/auth/mfa", "/auth/mfa/**").permitAll()
                .requestMatchers("/admin/**").hasRole("SUPER_ADMIN")
                .requestMatchers("/api/**").authenticated()
                .anyRequest().authenticated()
            )

            // ── CSRF ──────────────────────────────────────────────────────────
            // HttpSessionCsrfTokenRepository (padrão) — salva o token na sessão,
            // não depende de cookie no response. Evita o problema de buffer commit
            // que ocorria com CookieCsrfTokenRepository quando o CSS inline (~22KB)
            // esgotava o buffer do Tomcat antes de th:action ser processado.
            // /auth/logout é ignorado: quando a sessão expira o token CSRF some,
            // causando 403 no clique de logout. Forçar logout via CSRF é risco baixo.
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/**", "/auth/logout")
            )

            // ── Form login ────────────────────────────────────────────────────
            // usernameParameter: o formulário usa "email", não "username"
            .formLogin(form -> form
                .loginPage("/auth/login")
                .loginProcessingUrl("/auth/login")
                .usernameParameter("email")
                .passwordParameter("password")
                .successHandler(authSuccessHandler)
                .failureUrl("/auth/login?error")
                .permitAll()
            )

            // ── Logout ────────────────────────────────────────────────────────
            .logout(logout -> logout
                .logoutUrl("/auth/logout")
                .logoutSuccessUrl("/auth/login?logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID", "remember-me")
                .clearAuthentication(true)
                .permitAll()
            )

            // ── Sessão ────────────────────────────────────────────────────────
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .invalidSessionUrl("/auth/login")
                .maximumSessions(5)
                .expiredUrl("/auth/login?expired")
            )

            // ── Remember-me ───────────────────────────────────────────────────
            .rememberMe(rm -> rm
                .userDetailsService(userDetailsService)
                .tokenValiditySeconds(30 * 24 * 60 * 60)
                .key(rememberMeKey)
                .rememberMeParameter("rememberMe")
            )

            // ── UserDetailsService ────────────────────────────────────────────
            .userDetailsService(userDetailsService)

            // ── Headers de segurança ──────────────────────────────────────────
            .headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin())
                .contentTypeOptions(ct -> {})
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000)
                )
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'self'; " +
                    "script-src 'self' 'unsafe-inline' https://cdnjs.cloudflare.com; " +
                    "style-src 'self' 'unsafe-inline'; " +
                    "img-src 'self' data: blob:; " +
                    "connect-src 'self' ws://localhost:* wss://localhost:*; " +
                    "font-src 'self'; " +
                    "frame-ancestors 'self'"
                ))
                .referrerPolicy(ref -> ref.policy(
                    ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN
                ))
            )

            // ── CORS ──────────────────────────────────────────────────────────
            .cors(cors -> cors.disable());

        return http.build();
    }
}
