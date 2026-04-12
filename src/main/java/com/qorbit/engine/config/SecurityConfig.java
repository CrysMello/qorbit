package com.qorbit.engine.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuração de segurança HTTP do Qorbit.
 *
 * Modo atual: acesso livre (ferramenta local).
 * A autenticação pode ser habilitada futuramente via QORBIT_APP_PASSWORD.
 *
 * O que esta classe faz mesmo sem autenticação:
 *  - Desabilita CSRF explicitamente (app usa REST/JSON + WebSocket, sem form submissions)
 *  - Desabilita form login e basic auth gerados automaticamente pelo Spring Security
 *  - Adiciona X-Frame-Options: SAMEORIGIN (proteção contra clickjacking)
 *  - Mantém CORS gerenciado pelo CorsConfig (WebMvcConfigurer)
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Permite todas as requisições sem autenticação
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()
            )
            // CSRF desabilitado: app usa exclusivamente REST/JSON e WebSocket
            // Não há form submissions tradicionais que precisem de proteção CSRF
            .csrf(csrf -> csrf.disable())
            // CORS gerenciado pelo CorsConfig via WebMvcConfigurer
            .cors(cors -> cors.disable())
            // Desativa login form e basic auth automáticos do Spring Security
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .logout(logout -> logout.disable())
            // Headers de segurança HTTP
            .headers(headers -> headers
                // Proteção contra clickjacking
                .frameOptions(frame -> frame.sameOrigin())
                // Impede MIME-type sniffing (ex.: tratar JS como HTML)
                .contentTypeOptions(ct -> {})
                // Força HTTPS em navegadores que já visitaram o site
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000)
                )
                // Content-Security-Policy: restringe origens de scripts e estilos
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'self'; " +
                    "script-src 'self' 'unsafe-inline' https://cdnjs.cloudflare.com; " +
                    "style-src 'self' 'unsafe-inline'; " +
                    "img-src 'self' data: blob:; " +
                    "connect-src 'self' ws://localhost:* wss://localhost:*; " +
                    "font-src 'self'; " +
                    "frame-ancestors 'self'"
                ))
                // Controla quais informações são enviadas no cabeçalho Referer
                .referrerPolicy(ref -> ref.policy(
                    org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN
                ))
            );

        return http.build();
    }
}
