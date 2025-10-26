package com.paybridge.Filters;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.io.IOException;
import java.util.Set;

public class CookieAuthenticationFilter extends AbstractAuthenticationProcessingFilter {

    private final JwtDecoder jwtDecoder;
    private final JwtAuthenticationConverter jwtAuthenticationConverter;

    public CookieAuthenticationFilter(RequestMatcher matcher, JwtDecoder jwtDecoder,
                                      JwtAuthenticationConverter jwtAuthenticationConverter) {
        super(matcher);
        this.jwtDecoder = jwtDecoder;
        this.jwtAuthenticationConverter = jwtAuthenticationConverter;
    }


    @Override
    public boolean requiresAuthentication(HttpServletRequest request, HttpServletResponse response) {
        String URI = request.getRequestURI();
        Set<String> uriContainsRoutes = Set.of("/api/v1/merchants", "/api/v1/auth");

        Set<String> equals = Set.of("/");
        boolean uriContains = uriContainsRoutes.stream().anyMatch(URI::contains);
        boolean uriEquals = equals.stream().anyMatch(URI::equals);


        if(uriContains || uriEquals){
            return false;
        }

        return super.requiresAuthentication(request, response);
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String token = getJwtFromCookie(request);

        if (token == null) {
            return null;
        }
        Jwt jwt = jwtDecoder.decode(token);
        return jwtAuthenticationConverter.convert(jwt);
    }

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

    @Override
    protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response,
                                            FilterChain chain, Authentication authResult) throws IOException, ServletException {
        SecurityContextHolder.getContext().setAuthentication(authResult);
        chain.doFilter(request, response);
    }

}
