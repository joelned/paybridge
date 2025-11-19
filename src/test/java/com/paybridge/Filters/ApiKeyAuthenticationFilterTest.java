package com.paybridge.Filters;

import com.paybridge.Models.Entities.Merchant;
import com.paybridge.Models.Enums.MerchantStatus;
import com.paybridge.Repositories.MerchantRepository;
import com.paybridge.Services.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthenticationFilterTest {

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private ApiKeyService apiKeyService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private ApiKeyAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_NoApiKey_ContinuesChain() throws ServletException, IOException {
        // Given
        when(request.getHeader("x-api-key")).thenReturn(null);

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(apiKeyService);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilterInternal_RateLimitExceeded_Returns429() throws ServletException, IOException {
        // Given
        when(request.getHeader("x-api-key")).thenReturn("test-key");
        when(apiKeyService.checkRateLimit("test-key")).thenReturn(false);
        
        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(writer);

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        verify(response).setStatus(429);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void doFilterInternal_ValidKey_SetsAuthentication() throws ServletException, IOException {
        // Given
        String apiKey = "valid-key";
        Merchant merchant = new Merchant();
        merchant.setId(1L);
        merchant.setEmail("test@merchant.com");
        merchant.setStatus(MerchantStatus.ACTIVE);

        when(request.getHeader("x-api-key")).thenReturn(apiKey);
        when(apiKeyService.checkRateLimit(apiKey)).thenReturn(true);
        when(apiKeyService.findMerchantByApiKey(apiKey)).thenReturn(Optional.of(merchant));
        when(merchantRepository.hasMerchantEnabledUser(1L)).thenReturn(true);
        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getMethod()).thenReturn("GET");

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("test@merchant.com");
        verify(apiKeyService).logApiKeyUsageToRedis(eq(merchant), eq(apiKey), any(), any(), any(), any(), anyInt());
    }

    @Test
    void doFilterInternal_DisabledMerchant_Returns401() throws ServletException, IOException {
        // Given
        String apiKey = "valid-key";
        Merchant merchant = new Merchant();
        merchant.setId(1L);
        merchant.setStatus(MerchantStatus.SUSPENDED);

        when(request.getHeader("x-api-key")).thenReturn(apiKey);
        when(apiKeyService.checkRateLimit(apiKey)).thenReturn(true);
        when(apiKeyService.findMerchantByApiKey(apiKey)).thenReturn(Optional.of(merchant));
        when(merchantRepository.hasMerchantEnabledUser(1L)).thenReturn(true); // User enabled but merchant suspended

        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(writer);

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void shouldNotFilter_PublicUrl_ReturnsTrue() throws ServletException {
        // Given
        when(request.getRequestURI()).thenReturn("/api/v1/auth/login");

        // When
        boolean result = filter.shouldNotFilter(request);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void shouldNotFilter_ProtectedUrl_ReturnsFalse() throws ServletException {
        // Given
        when(request.getRequestURI()).thenReturn("/api/v1/merchants/profile");

        // When
        boolean result = filter.shouldNotFilter(request);

        // Then
        assertThat(result).isFalse();
    }
}
