package com.paybridge.Filters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.paybridge.Models.DTOs.ApiResponse;
import com.paybridge.Models.DTOs.ErrorDetail;
import com.paybridge.Models.Enums.ApiErrorCode;
import com.paybridge.Security.SecurityConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Mitigates CSRF for JWT cookie authenticated requests by validating Origin/Referer
 * on state-changing methods. API key requests are excluded.
 */
@Component
public class CookieCsrfProtectionFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "x-api-key";
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final Set<String> allowedOrigins;

    public CookieCsrfProtectionFilter(@Value("${cors.allowed-origins}") String allowedOriginsConfig) {
        this.allowedOrigins = Arrays.stream(allowedOriginsConfig.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .collect(Collectors.toSet());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (SAFE_METHODS.contains(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        if (request.getHeader(API_KEY_HEADER) != null && !request.getHeader(API_KEY_HEADER).isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!hasJwtCookie(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String origin = request.getHeader("Origin");
        String referer = request.getHeader("Referer");

        // Non-browser or same-service clients may omit both headers.
        if ((origin == null || origin.isBlank()) && (referer == null || referer.isBlank())) {
            filterChain.doFilter(request, response);
            return;
        }

        boolean originAllowed = origin != null && isAllowedOrigin(origin);
        boolean refererAllowed = referer != null && isAllowedReferer(referer);

        if (!originAllowed && !refererAllowed) {
            writeErrorResponse(response, HttpServletResponse.SC_FORBIDDEN,
                    ErrorDetail.of("CSRF validation failed for cookie-authenticated request", ApiErrorCode.UNAUTHORIZED),
                    request.getRequestURI());
            return;
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        AntPathMatcher pathMatcher = new AntPathMatcher();
        for (String pattern : SecurityConstants.PUBLIC_URLS) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasJwtCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return false;
        }
        for (Cookie cookie : request.getCookies()) {
            if ("jwt".equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return true;
            }
        }
        return false;
    }

    private boolean isAllowedOrigin(String origin) {
        return allowedOrigins.contains(origin);
    }

    private boolean isAllowedReferer(String referer) {
        try {
            URI uri = URI.create(referer);
            String candidate = uri.getScheme() + "://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
            return allowedOrigins.contains(candidate);
        } catch (Exception e) {
            return false;
        }
    }

    private void writeErrorResponse(HttpServletResponse response, int status, ErrorDetail error, String path) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiResponse<?> apiResponse = ApiResponse.error(error, path);
        response.getWriter().write(OBJECT_MAPPER.writeValueAsString(apiResponse));
    }
}
