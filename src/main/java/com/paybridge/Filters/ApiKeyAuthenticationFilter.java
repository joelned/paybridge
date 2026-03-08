package com.paybridge.Filters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.paybridge.Models.DTOs.ApiResponse;
import com.paybridge.Models.DTOs.ErrorDetail;
import com.paybridge.Models.Entities.Merchant;
import com.paybridge.Models.Enums.ApiErrorCode;
import com.paybridge.Models.Enums.MerchantStatus;
import com.paybridge.Repositories.MerchantRepository;
import com.paybridge.Repositories.UserRepository;
import com.paybridge.Security.SecurityConstants;
import com.paybridge.Services.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

/**
 * Filter for authenticating requests using API keys sent in the x-api-key header.
 * This filter runs before JWT authentication.
 */
@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private UserRepository userRepository;

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

        if(request.getHeader(API_KEY_HEADER) == null || request.getHeader(API_KEY_HEADER).isBlank()){
            filterChain.doFilter(request, response);
            return;
        }

        String apiKey = request.getHeader(API_KEY_HEADER);

        // If no API key header, continue to next filter (JWT authentication)
        if (apiKey == null || apiKey.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        // Check rate limit
        if (!apiKeyService.checkRateLimit(apiKey)) {
            writeErrorResponse(response, 429, ErrorDetail.of("Rate limit exceeded", ApiErrorCode.RATE_LIMIT_EXCEEDED), request.getRequestURI());
            return;
        }
        // Find merchant by API key (hashed lookup with legacy fallback)
        Optional<Merchant> merchantOpt = apiKeyService.findMerchantByApiKey(apiKey);

        if (merchantOpt.isPresent()) {
            Merchant merchant = merchantOpt.get();
            if (!merchantRepository.hasMerchantEnabledUser(merchant.getId()) ||
                    merchant.getStatus() == MerchantStatus.SUSPENDED) {

                writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                        ErrorDetail.of("Account has been disabled. Please contact support", ApiErrorCode.ACCOUNT_DISABLED),
                        request.getRequestURI());
                return;
            }
            if (merchant.getStatus() == MerchantStatus.PENDING_PROVIDER_SETUP) {
                writeErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                        ErrorDetail.of("Please configure at least one provider to use api key", ApiErrorCode.PROVIDER_NOT_CONFIGURED),
                        request.getRequestURI());
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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private void writeErrorResponse(HttpServletResponse response, int status, ErrorDetail error, String path) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiResponse<?> apiResponse = ApiResponse.error(error, path);
        response.getWriter().write(OBJECT_MAPPER.writeValueAsString(apiResponse));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        AntPathMatcher pathMatcher = new AntPathMatcher();

        for (String pattern : SecurityConstants.PUBLIC_URLS) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }
}