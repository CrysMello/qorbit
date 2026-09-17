package com.qorbit.engine.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                    // Permite local e domínios de produção com proxy reverso.
                    .allowedOriginPatterns(
                        "http://localhost:*",
                        "http://127.0.0.1:*",
                        "http://*.duckdns.org",
                        "https://*.duckdns.org"
                    )
                    .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                    .allowedHeaders("*")
                    .allowCredentials(true);

                // Captura client-side da gravação (extensão de navegador, fluxo do VSCode):
                // roda na aba do site sob teste (origem arbitrária), por isso precisa aceitar
                // qualquer origem. Sem cookies/credenciais — autorização via token no corpo.
                registry.addMapping("/captura-publica/**")
                    .allowedOriginPatterns("*")
                    .allowedMethods("GET", "POST", "OPTIONS")
                    .allowedHeaders("*")
                    .allowCredentials(false);
            }
        };
    }
}
