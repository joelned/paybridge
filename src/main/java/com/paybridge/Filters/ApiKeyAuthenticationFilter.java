package com.paybridge.Filters;

import com.paybridge.Models.Entities.Merchant;
import com.paybridge.Models.Enums.MerchantStatus;
import com.paybridge.Repositories.MerchantRepository;
import com.paybridge.Services.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.Optional;

/**
 * Filter for authenticating requests using API keys sent in the x-api-key header.
 * This filter runs before JWT authentication.
 */
@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "x-api-key";

    private final MerchantRepository merchantRepository;
    private final ApiKeyService apiKeyService;

    public ApiKeyAuthenticationFilter(MerchantRepository merchantRepository, ApiKeyService apiKeyService) {
        this.merchantRepository = merchantRepository;
        this.apiKeyService = apiKeyService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String apiKey = request.getHeader(API_KEY_HEADER);

        // If no API key header, continue to next filter (JWT authentication)
        if (apiKey == null || apiKey.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        // Check rate limit
        if (!apiKeyService.checkRateLimit(apiKey)) {
            response.setStatus(429);
            response.setContentType("application/json");
            try (PrintWriter writer = response.getWriter()) {
                writer.write(
                        "{\"error\":\"Rate limit exceeded\", \"message\":\"You have exceeded your API rate limit. " +
                                "Please try again later.\"}"
                );
            }
            return;
        }
        // Find merchant by API key (hashed lookup with legacy fallback)
        Optional<Merchant> merchantOpt = apiKeyService.findMerchantByApiKey(apiKey);

        if (merchantOpt.isPresent()) {
            Merchant merchant = merchantOpt.get();

            if (!merchantRepository.hasMerchantEnabledUser(merchant.getId()) || merchant.getStatus() != null ||
            merchant.getStatus() == MerchantStatus.SUSPENDED || merchant.getStatus() == MerchantStatus.INACTIVE) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write(
                        "{\"error\":\"Account disabled\", \"message\":\"This account has been disabled. " +
                                "Please contact support.\"}"
                );
                return;
            }
            // Create authentication token
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            merchant.getEmail(),
                            null,
                            Collections.singletonList(new SimpleGrantedAuthority("ROLE_MERCHANT"))
                    );

            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            // Set authentication in security context
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // Log API usage asynchronously
            String path = request.getRequestURI();
            String method = request.getMethod();
            String ip = apiKeyService.getClientIpAddress(request);
            String userAgent = request.getHeader("User-Agent");

            apiKeyService.logApiKeyUsageToRedis(merchant, apiKey, path, method, ip, userAgent, 0);
        }
        else{
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();

        // Don't filter public endpoints
        return path.startsWith("/api/v1/auth/") ||
                path.equals("/api/v1/merchants");
    }
}