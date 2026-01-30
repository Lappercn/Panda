package org.AI.panda.auth.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.AI.panda.auth.service.SessionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class AuthUserIdFilter extends OncePerRequestFilter {
    public static final String ATTR_AUTH_USER_ID = "AUTH_USER_ID";

    private final SessionService sessionService;

    @Value("${panda.auth.session.cookie-name:PANDA_SESSION}")
    private String cookieName;

    public AuthUserIdFilter(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = readCookie(request, cookieName);
        if (token != null && !token.isBlank()) {
            String userId = sessionService.resolveUserId(token);
            if (userId != null && !userId.isBlank()) {
                request.setAttribute(ATTR_AUTH_USER_ID, userId);
            }
        }
        filterChain.doFilter(request, response);
    }

    private String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null || name == null || name.isBlank()) return null;
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
