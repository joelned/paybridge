package com.paybridge.integration;

import com.paybridge.Models.DTOs.MerchantRegistrationRequest;
import com.paybridge.Models.DTOs.VerifyEmailRequest;
import com.paybridge.Models.Entities.Merchant;
import com.paybridge.Models.Entities.Users;
import com.paybridge.Services.ApiKeyService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Fixed integration tests for API Key functionality with proper Redis mocking
 */
public class ApiKeyIntegrationTestFixed extends BaseIntegrationTest {

    @MockBean
    private ApiKeyService apiKeyService;

    @Test
    void apiKeyAuthentication_ValidTestKey_ShouldAuthenticate() throws Exception {
        String email = "apiauth@example.com";
        
        // Mock rate limit check to always pass
        when(apiKeyService.checkRateLimit(anyString())).thenReturn(true);
        
        // 1. Register + verify merchant
        registerAndVerifyMerchant(email, "Password123");

        // 2. Get the merchant and API key from DB
        Merchant merchant = merchantRepository.findByEmail(email);
        assertNotNull(merchant, "Merchant should exist after verification");
        assertNotNull(merchant.getApiKeyTest(), "Test API key should not be null");

        // 3. Perform the secured request with the test key
        mockMvc.perform(get("/api/v1/get-apikey")
                        .header("x-api-key", merchant.getApiKeyTest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.api_key_test").exists())
                .andExpect(jsonPath("$.api_key_live").exists());
    }

    @Test
    void apiKeyAuthentication_ValidLiveKey_ShouldAuthenticate() throws Exception {
        String email = "apilive@example.com";
        
        // Mock rate limit check to always pass
        when(apiKeyService.checkRateLimit(anyString())).thenReturn(true);
        
        // 1. Register and verify merchant
        registerAndVerifyMerchant(email, "Password123");

        // 2. Get the live API key
        Merchant merchant = merchantRepository.findByEmail(email);
        String liveApiKey = merchant.getApiKeyLive();

        // 3. Make a request with the live API key
        mockMvc.perform(get("/api/v1/get-apikey")
                        .header("x-api-key", liveApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.api_key_live").exists());
    }

    @Test
    void apiKeyAuthentication_InvalidKey_ShouldReturn401() throws Exception {
        // Mock rate limit check to always pass for any key
        when(apiKeyService.checkRateLimit(anyString())).thenReturn(true);
        
        mockMvc.perform(get("/api/v1/get-apikey")
                        .header("x-api-key", "pk_test_invalid_key_12345"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void apiKeyAuthentication_RateLimitExceeded_ShouldReturn429() throws Exception {
        String email = "ratelimit@example.com";
        
        // Mock rate limit check to fail
        when(apiKeyService.checkRateLimit(anyString())).thenReturn(false);
        
        registerAndVerifyMerchant(email, "Password123");
        Merchant merchant = merchantRepository.findByEmail(email);

        mockMvc.perform(get("/api/v1/get-apikey")
                        .header("x-api-key", merchant.getApiKeyTest()))
                .andExpect(status().isTooManyRequests());
    }

    // Helper method
    private void registerAndVerifyMerchant(String email, String password) throws Exception {
        MerchantRegistrationRequest request = createRegistrationRequest();
        request.setEmail(email);
        request.setPassword(password);

        mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isCreated());

        Users user = userRepository.findByEmail(email);
        VerifyEmailRequest verifyRequest = new VerifyEmailRequest(email, user.getVerificationCode());

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(verifyRequest)))
                .andExpect(status().isOk());
    }

    private MerchantRegistrationRequest createRegistrationRequest() {
        MerchantRegistrationRequest request = new MerchantRegistrationRequest();
        request.setBusinessName("Test Business");
        request.setEmail("test" + System.currentTimeMillis() + "@example.com");
        request.setPassword("Password123");
        request.setBusinessType("ECOMMERCE");
        request.setBusinessCountry("US");
        request.setWebsiteUrl("https://example.com");
        return request;
    }
}