package com.ingredientapp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.MapSession;
import org.springframework.session.MapSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.config.annotation.web.http.EnableSpringHttpSession;
import org.springframework.session.web.http.HeaderHttpSessionIdResolver;
import org.springframework.session.web.http.HttpSessionIdResolver;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/* Switches session tracking from a cookie to a plain request/response header.
   The front-end and back-end are on two different Render subdomains, and Safari's
   Intelligent Tracking Prevention blocks that kind of cross-site cookie by default,
   even with SameSite=None; Secure set. That meant ingredients scanned on an iPhone
   were detected correctly but never showed up afterwards, since the browser refused
   to store the session cookie, so every following request looked like a brand new,
   empty session. A header isn't a cookie, so ITP has nothing to block here.
   MapSessionRepository keeps sessions in memory only, matching the no-database
   design (FR8, SR4); IngredientController is unchanged, since it still just reads
   and writes attributes on HttpSession. */
@Configuration
@EnableSpringHttpSession
public class SessionConfig {

    public static final String SESSION_HEADER_NAME = "X-Session-Id";

    @Bean
    public SessionRepository<MapSession> sessionRepository() {
        Map<String, Session> sessions = new ConcurrentHashMap<>();
        return new MapSessionRepository(sessions);
    }

    @Bean
    public HttpSessionIdResolver httpSessionIdResolver() {
        return new HeaderHttpSessionIdResolver(SESSION_HEADER_NAME);
    }
}
