package com.qorbit.engine.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.HandlerInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // ── Cache para arquivos estáticos ─────────────────────────────────
        registry
            .addResourceHandler("/images/**")
            .addResourceLocations("classpath:/static/images/")
            .setCacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic());

        registry
            .addResourceHandler("/css/**")
            .addResourceLocations("classpath:/static/css/")
            .setCacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic());

        registry
            .addResourceHandler("/js/**")
            .addResourceLocations("classpath:/static/js/")
            .setCacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic());

        registry
            .addResourceHandler("/favicon.ico")
            .addResourceLocations("classpath:/static/favicon.ico")
            .setCacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic());
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // ── Remover cache-control: no-store dos HTMLs ──────────────────────
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public void postHandle(HttpServletRequest request, HttpServletResponse response,
                                 Object handler, org.springframework.web.servlet.ModelAndView modelAndView) {
                // Apenas para templates HTML (não para APIs)
                if (!request.getRequestURI().startsWith("/api/")) {
                    // Remover o cache-control padrão restritivo
                    response.setHeader("Cache-Control", "public, max-age=3600");
                    response.setHeader("Pragma", "");
                }
            }
        });
    }
}
