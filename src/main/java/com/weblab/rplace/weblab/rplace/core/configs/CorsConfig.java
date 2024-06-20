package com.weblab.rplace.weblab.rplace.core.configs;

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
                registry
                    .addMapping("/api/**")
                    .allowedOrigins("https://place.yildizskylab.com", "http://place.yildizskylab.com","http://localhost:3000")
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
