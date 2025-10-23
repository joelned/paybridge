package com.paybridge.Filters;

import com.paybridge.Models.Entities.Merchant;
import com.paybridge.Security.ApiKeyAuthentication;
import com.paybridge.Services.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final String API_KEY_HEADER = "X-API-Key";
    private final Logger logger = LoggerFactory.getLogger(ApiKeyAuthenticationFilter.class);

    private final ApiKeyService apiKeyService;

    public ApiKeyAuthenticationFilter(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String apiKey = extractApiKey(request);

        if(apiKey != null){
            try{
                if(!apiKeyService.checkRateLimit(apiKey)){
                    response.setStatus(429);
                    response.setContentType("application/json");
                    response.getWriter().write(
                            "{\"error\":\"Rate limit exceeded\",\"message\":\"You have exceeded your API rate limit." +
                                    " Please try again later.\"}"
                    );
                }
                Optional<Merchant> merchantOpt = apiKeyService.validateApiKey(apiKey);

                if(merchantOpt.isPresent()){
                    Merchant merchant = merchantOpt.get();

                    boolean isTestMode = apiKeyService.isTestMode(apiKey);

                    ApiKeyAuthentication apiKeyAuthentication = new ApiKeyAuthentication(
                           apiKey, isTestMode, merchant
                    );

                    SecurityContextHolder.getContext().setAuthentication(apiKeyAuthentication);

                    logger.debug("API Key authenticated for merchant: {} ({})",
                            merchant.getBusinessName(),
                            isTestMode ? "TEST" : "LIVE");

                    apiKeyService.logApiKeyUsageToRedis(merchant, apiKey, request, response.getStatus());
                }
                else{
                    logger.warn("Invalid API key attempt from IP: {}", request.getRemoteAddr());
                }
            }catch (Exception ex){
                logger.error("Error occured authenticating API key ", ex);
            }
        }
        filterChain.doFilter(request, response);
    }

    private String extractApiKey(HttpServletRequest request){
        String apiKey = request.getHeader(API_KEY_HEADER);

        if(apiKey == null){
            String authorization = request.getHeader("Authorization");
            if(authorization != null && authorization.startsWith("Bearer pk_")){
                return authorization.substring(7);
            }
        }

        return apiKey;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request){
        String path = request.getRequestURI();

        return path.startsWith("/api/v1/auth") ||
                path.startsWith("/api/v1/merchants");
    }

}
