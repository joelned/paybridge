package com.paybridge.Filters;

import com.paybridge.Models.Entities.Merchant;
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
import java.util.Collections;
import java.util.Optional;

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

        if (apiKey == null || apiKey.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        if(!apiKeyService.checkRateLimit(apiKey)){
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\":\"Rate limit exceeded\", \"message\":\"You have exceeded your API rate limit. " +
                            "Please try again later.\"}"
            );
            return;
        }

        Optional<Merchant> merchantOpt = merchantRepository.findByApiKeyTestOrApiKeyLive(apiKey);

        if (merchantOpt.isPresent()) {
            Merchant merchant = merchantOpt.get();

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            merchant.getEmail(),
                            null,
                            Collections.singletonList(new SimpleGrantedAuthority("ROLE_MERCHANT"))
                    );

            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);
            apiKeyService.logApiKeyUsageToRedis(merchant, apiKey, request, response.getStatus());
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        return path.startsWith("/api/v1/auth/") ||
                path.equals("/api/v1/merchants");
    }
}