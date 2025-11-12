package com.paybridge.integration;

import com.paybridge.Models.DTOs.LoginRequest;
import com.paybridge.Models.DTOs.MerchantRegistrationRequest;
import com.paybridge.Models.DTOs.VerifyEmailRequest;
import com.paybridge.Models.Entities.Merchant;
import com.paybridge.Models.Entities.Users;
import com.paybridge.Services.EmailProvider;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

import jakarta.servlet.http.Cookie;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for API Key functionality including generation, retrieval, and usage
 */
public class ApiKeyIntegrationTest extends BaseIntegrationTest {

    @MockitoBean
    private EmailProvider emailProvider;

    @Test
    void getApiKey_WithoutAuthentication_ShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/test-controller"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void apiKeyAuthentication_InvalidKey_ShouldNotAuthenticate() throws Exception {
        mockMvc.perform(get("/api/v1/test-controller")
                        .header("x-api-key", "pk_test_invalid_key_12345"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void apiKeyAuthentication_EmptyKey_ShouldNotAuthenticate() throws Exception {
        mockMvc.perform(get("/api/v1/test-controller")
                        .header("x-api-key", ""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void apiKeyAuthentication_MalformedKey_ShouldNotAuthenticate() throws Exception {
        mockMvc.perform(get("/api/v1/test-controller")
                        .header("x-api-key", "not-a-valid-format"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void apiKey_UniquePerMerchant() throws Exception {
        // 1. Register first merchant
        String email1 = "merchant1@example.com";
        registerAndVerifyMerchant(email1, "Password123$");
        Merchant merchant1 = merchantRepository.findByEmail(email1);

        // 2. Register second merchant
        String email2 = "merchant2@example.com";
        registerAndVerifyMerchant(email2, "Password123$");
        Merchant merchant2 = merchantRepository.findByEmail(email2);

        // 3. Verify different API keys
        assertNotEquals(merchant1.getApiKeyTest(), merchant2.getApiKeyTest());
        assertNotEquals(merchant1.getApiKeyLive(), merchant2.getApiKeyLive());
    }

    @Test
    void apiKey_TestModeFlag_CorrectlySet() throws Exception {
        // Register and verify merchant
        String email = "testmode@example.com";
        registerAndVerifyMerchant(email, "Password123$");

        // Get merchant and verify test mode is true by default
        Merchant merchant = merchantRepository.findByEmail(email);
        assertTrue(merchant.isTestMode());

        // Active key should be test key
        assertEquals(merchant.getApiKeyTest(), merchant.getActiveKey());
    }

    @Test
    void apiKey_Format_CorrectPrefix() throws Exception {
        // Register and verify
        String email = "format@example.com";
        registerAndVerifyMerchant(email, "Password123$");

        Merchant merchant = merchantRepository.findByEmail(email);

        // Test key should start with pk_test_
        assertTrue(merchant.getApiKeyTest().startsWith("pk_test_"));

        // Live key should start with pk_live_
        assertTrue(merchant.getApiKeyLive().startsWith("pk_live_"));

        // Keys should be long enough (at least 40 characters)
        assertTrue(merchant.getApiKeyTest().length() > 40);
        assertTrue(merchant.getApiKeyLive().length() > 40);
    }

    @Test
    void apiKey_GeneratedOnEmailVerification() throws Exception {
        // 1. Register (don't verify yet)
        MerchantRegistrationRequest request = createRegistrationRequest();
        request.setEmail("beforeverify@example.com");

        mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isCreated());

        // 2. Check API keys before verification (should be null)
        Merchant merchantBeforeVerify = merchantRepository.findByEmail("beforeverify@example.com");
        assertNull(merchantBeforeVerify.getApiKeyTest());
        assertNull(merchantBeforeVerify.getApiKeyLive());

        // 3. Verify email
        Users user = userRepository.findByEmail("beforeverify@example.com");
        VerifyEmailRequest verifyRequest = new VerifyEmailRequest("beforeverify@example.com",
                user.getVerificationCode());

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(verifyRequest)))
                .andExpect(status().isOk());

        // 4. Check API keys after verification (should exist)
        Merchant merchantAfterVerify = merchantRepository.findByEmail("beforeverify@example.com");
        assertNotNull(merchantAfterVerify.getApiKeyTest());
        assertNotNull(merchantAfterVerify.getApiKeyLive());
    }

    @Test
    void apiKey_DisabledUser_ShouldNotAuthenticate() throws Exception {
        String email = "disabled@example.com";
        registerAndVerifyMerchant(email, "Password123$");
        Merchant merchant = merchantRepository.findByEmail(email);

        Users merchantUser= userRepository.findByMerchant(merchant);
        merchantUser.setEnabled(false);
        userRepository.save(merchantUser);

        mockMvc.perform(get("/api/v1/get-apikey")
                        .header("x-api-key", merchant.getApiKeyTest()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void apiKey_ShouldBeUniqueInDatabase() {
        var allKeys = merchantRepository.findAll()
                .stream()
                .flatMap(m -> java.util.stream.Stream.of(m.getApiKeyTest(), m.getApiKeyLive()))
                .collect(java.util.stream.Collectors.toSet());

        long totalCount = merchantRepository.count() * 2;
        assertEquals(totalCount, allKeys.size(), "API keys must be globally unique");
    }

    // Helper methods
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

    private Cookie registerVerifyAndLogin(String email, String password) throws Exception {
        registerAndVerifyMerchant(email, password);

        LoginRequest loginRequest = new LoginRequest(email, password);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String setCookieHeader = result.getResponse().getHeader("Set-Cookie");
        assertNotNull(setCookieHeader);

        // Extract JWT value from Set-Cookie header
        String jwtValue = setCookieHeader.split(";")[0].split("=")[1];
        return new Cookie("jwt", jwtValue);
    }

    private MerchantRegistrationRequest createRegistrationRequest() {
        MerchantRegistrationRequest request = new MerchantRegistrationRequest();
        request.setBusinessName("Test Business");
        request.setEmail("test" + System.currentTimeMillis() + "@example.com");
        request.setPassword("Password123$");
        request.setBusinessType("ECOMMERCE");
        request.setBusinessCountry("US");
        request.setWebsiteUrl("https://example.com");
        return request;
    }

}