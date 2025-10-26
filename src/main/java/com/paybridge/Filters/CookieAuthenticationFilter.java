package com.paybridge.Filters;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter for authenticating requests using JWT tokens stored in HttpOnly cookies.
 * This filter runs for all requests except public endpoints.
 */
public class CookieAuthenticationFilter extends OncePerRequestFilter {

    private final JwtDecoder jwtDecoder;
    private final JwtAuthenticationConverter jwtAuthenticationConverter;

    public CookieAuthenticationFilter(JwtDecoder jwtDecoder,
                                      JwtAuthenticationConverter jwtAuthenticationConverter) {
        this.jwtDecoder = jwtDecoder;
        this.jwtAuthenticationConverter = jwtAuthenticationConverter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Skip if already authenticated (e.g., by API key filter)
        if (SecurityContextHolder.getContext().getAuthentication() != null &&
                SecurityContextHolder.getContext().getAuthentication().isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String token = getJwtFromCookie(request);

            if (token != null && !token.isEmpty()) {
                // Decode and validate JWT
                Jwt jwt = jwtDecoder.decode(token);

                // Convert JWT to Authentication
                Authentication authentication = jwtAuthenticationConverter.convert(jwt);

                if (authentication != null) {
                    // Set authentication in context
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        } catch (JwtException e) {
            // Log JWT validation failure but don't stop the filter chain
            logger.debug("JWT validation failed: " + e.getMessage());
            // Clear any partial authentication
            SecurityContextHolder.clearContext();
        } catch (Exception e) {
            // Log unexpected errors
            logger.error("Error processing JWT from cookie: " + e.getMessage(), e);
            SecurityContextHolder.clearContext();
        }

        // Always continue the filter chain
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();

        // Don't filter public endpoints
        return path.startsWith("/api/v1/auth/") ||
                path.equals("/api/v1/merchants") ||
                path.equals("/");
    }

    /**
     * Extract JWT token from the 'jwt' cookie
     */
    private String getJwtFromCookie(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("jwt".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}