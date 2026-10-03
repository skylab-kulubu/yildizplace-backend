package com.weblab.rplace.weblab.rplace.core.configs;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Origins that may call /api/** from a browser with the login cookies. Production allows
 * the https frontend alone (place.cors.allowed-origins in application.properties); the
 * frontend's dev server (http://localhost:3000) is added only by the dev profile
 * (application-dev.properties). A request from any other origin gets 403 before the
 * endpoint runs, which is what stops a sibling subdomain (SecurityConfig).
 */
@Configuration
public class CorsConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer(@Value("${place.cors.allowed-origins}") String[] allowedOrigins) {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry
                    .addMapping("/api/**")
                    .allowedOrigins(allowedOrigins)
                    .allowedMethods("GET", "POST", "PUT", "DELETE")
                        .allowedHeaders(
                                "Access-Control-Allow-Headers",
                                "Access-Control-Allow-Origin",
                                "Access-Control-Request-Method",
                                "Access-Control-Request-Headers",
                                "Origin",
                                "Cache-Control",
                                "Content-Type",
                                "Authorization")
                        .exposedHeaders(
                                "Access-Control-Allow-Headers",
                                "Access-Control-Allow-Origin",
                                "Access-Control-Request-Method",
                                "Access-Control-Request-Headers",
                                "Origin",
                                "Cache-Control",
                                "Content-Type",
                                "Authorization")
                        .allowCredentials(true);
            }
        };
    }
}
