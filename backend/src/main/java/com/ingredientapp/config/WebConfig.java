package com.ingredientapp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS configuration.
 *
 * The front-end (Render Static Site) and back-end (Render Web Service) are deployed on two
 * different origins, but the ingredient list is tracked via an HTTP session cookie
 * (see Chapter 3: Back-End Design). A cross-origin request only carries that cookie if:
 *   1. the front-end's fetch() calls are made with `credentials: "include"`, and
 *   2. CORS explicitly allows credentials for the front-end's exact origin
 *      (allowCredentials(true) cannot be combined with allowedOrigins("*")), and
 *   3. the session cookie itself is issued with SameSite=None; Secure
 *      (see application.properties: server.servlet.session.cookie.same-site=none).
 *
 * app.cors.allowed-origins is a comma-separated list, configurable via an environment
 * variable on Render so the deployed front-end URL can be added without a code change.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.cors.allowed-origins:http://localhost:5500,http://127.0.0.1:5500}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.split(","))
                .allowedMethods("GET", "POST", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
