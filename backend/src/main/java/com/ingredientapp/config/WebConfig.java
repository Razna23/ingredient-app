package com.ingredientapp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/* CORS setup so the front-end (a different origin) can call this API. The session id
   travels as a plain X-Session-Id request/response header (SessionConfig) rather than
   a cookie, so the header has to be explicitly exposed, or the front-end's JS has no
   way to read it back off the response. Allowed origins come from an env var on Render. */
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
                .exposedHeaders(SessionConfig.SESSION_HEADER_NAME)
                .allowCredentials(true);
    }
}
