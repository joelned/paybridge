package com.paybridge.integration;

import com.paybridge.Models.DTOs.LoginRequest;
import com.paybridge.Models.DTOs.MerchantRegistrationRequest;
import com.paybridge.Models.DTOs.VerifyEmailRequest;
import com.paybridge.Models.Entities.Users;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import jakarta.servlet.http.Cookie;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for security configuration and access control
 */
public class SecurityIntegrationTest extends BaseIntegrationTest {

    @Test
    void security_PublicEndpoints_AccessibleWithoutAuth() throws Exception {
        // Registration endpoint should be public
        MerchantRegistrationRequest request = createRegistrationRequest();

        mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isCreated());
    }

    @Test
    void security_AuthEndpoints_AccessibleWithoutAuth() throws Exception {
        // Auth endpoints should be public
        LoginRequest loginRequest = new LoginRequest("test@example.com", "password");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(loginRequest)))
                .andExpect(status().isBadRequest()); // Bad request, not unauthorized
    }

    @Test
    void security_ProtectedEndpoint_WithoutAuth_Returns401() throws Exception {
        // Protected endpoint without auth should return 401
        mockMvc.perform(get("/api/v1/get-apikey"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void security_ProtectedEndpoint_WithValidJWT_ReturnsSuccess() throws Exception {
        // 1. Register, verify, and login
        String email = "protected@example.com";
        Cookie jwtCookie = registerVerifyAndLogin(email, "Password123");

        // 2. Access protected endpoint with JWT
        mockMvc.perform(get("/api/v1/get-apikey")
                        .cookie(jwtCookie))
                .andExpect(status().isOk());
    }

    @Test
    void security_ApiKeyAuth_InvalidKey_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/get-apikey")
                        .header("x-api-key", "invalid_key"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void security_ApiKeyHeader_Present_FilterProcesses() throws Exception {
        // Test that having x-api-key header doesn't cause errors
        // Even with invalid key, should return proper 401
        mockMvc.perform(get("/api/v1/get-apikey")
                        .header("x-api-key", "pk_test_invalid"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("X-Auth-Error")); // Should be clean 401
    }

    @Test
    void security_BothJWTAndApiKey_BothAuthMechanismsExist() throws Exception {
        // Test that both auth mechanisms are configured
        // JWT auth should work
        String email = "both@example.com";
        Cookie jwtCookie = registerVerifyAndLogin(email, "Password123");

        mockMvc.perform(get("/api/v1/get-apikey")
                        .cookie(jwtCookie))
                .andExpect(status().isOk());

        // API key auth should also be processed (even if key is invalid)
        mockMvc.perform(get("/api/v1/get-apikey")
                        .header("x-api-key", "pk_test_invalid"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void security_CORS_PreflightRequest_ReturnsCorrectHeaders() throws Exception {
        mockMvc.perform(options("/api/v1/merchants")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Access-Control-Allow-Origin"))
                .andExpect(header().exists("Access-Control-Allow-Methods"));
    }

    @Test
    void security_CORS_AllowedOrigin_AccessGranted() throws Exception {
        MerchantRegistrationRequest request = createRegistrationRequest();

        mockMvc.perform(post("/api/v1/merchants")
                        .header("Origin", "http://localhost:3000")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
    }

    @Test
    void security_PasswordEncryption_NotStoredInPlaintext() throws Exception {
        // 1. Register merchant
        String email = "encryption@example.com";
        String plainPassword = "Password123";

        MerchantRegistrationRequest request = createRegistrationRequest();
        request.setEmail(email);
        request.setPassword(plainPassword);

        mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isCreated());

        // 2. Verify password is encrypted in database
        Users user = userRepository.findByEmail(email);
        assertNotEquals(plainPassword, user.getPassword());
        assertTrue(user.getPassword().startsWith("$2")); // BCrypt hash prefix
    }

    @Test
    void security_JWTCookie_HttpOnlySet() throws Exception {
        // 1. Login
        String email = "cookie@example.com";
        registerAndVerifyMerchant(email, "Password123");

        LoginRequest loginRequest = new LoginRequest(email, "Password123");
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        // 2. Verify cookie attributes
        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertNotNull(setCookie);
        assertTrue(setCookie.contains("HttpOnly"));
        assertTrue(setCookie.contains("Secure"));
        assertTrue(setCookie.contains("SameSite=None"));
    }

    @Test
    void security_SensitiveDataNotExposed_InLoginResponse() throws Exception {
        // 1. Register and login
        String email = "sensitive@example.com";
        Cookie jwtCookie = registerVerifyAndLogin(email, "Password123");

        // 2. Verify token not in response body
        LoginRequest loginRequest = new LoginRequest(email, "Password123");
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andReturn();
    }

    @Test
    void security_CSRFDisabled_ForStatelessAPI() throws Exception {
        // CSRF should be disabled for stateless API
        // This test verifies POST works without CSRF token
        MerchantRegistrationRequest request = createRegistrationRequest();

        mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isCreated()); // Should work without CSRF token
    }

    @Test
    void security_SessionManagement_Stateless() throws Exception {
        // Verify no session is created
        MerchantRegistrationRequest request = createRegistrationRequest();

        MvcResult result = mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isCreated())
                .andReturn();

        // No session cookie should be set
        String sessionCookie = result.getResponse().getHeader("Set-Cookie");
        if (sessionCookie != null) {
            assertFalse(sessionCookie.contains("JSESSIONID"));
        }
    }

    @Test
    void security_MultipleAuthMechanisms_Work() throws Exception {
        // Test that both JWT and API key auth mechanisms are available
        String email = "multiauth@example.com";

        // 1. Register and verify
        registerAndVerifyMerchant(email, "Password123");

        // 2. Test JWT auth
        LoginRequest loginRequest = new LoginRequest(email, "Password123");
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String setCookie = loginResult.getResponse().getHeader("Set-Cookie");
        String jwtValue = setCookie.split(";")[0].split("=")[1];
        Cookie jwtCookie = new Cookie("jwt", jwtValue);

        // JWT should work
        mockMvc.perform(get("/api/v1/get-apikey")
                        .cookie(jwtCookie))
                .andExpect(status().isOk());

        // 3. API key header should be processed (returns 401 for invalid key)
        mockMvc.perform(get("/api/v1/get-apikey")
                        .header("x-api-key", "pk_test_invalid"))
                .andExpect(status().isUnauthorized());
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

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        String jwtValue = setCookie.split(";")[0].split("=")[1];
        return new Cookie("jwt", jwtValue);
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

    private void assertNotEquals(String expected, String actual) {
        org.junit.jupiter.api.Assertions.assertNotEquals(expected, actual);
    }

    private void assertTrue(boolean condition) {
        org.junit.jupiter.api.Assertions.assertTrue(condition);
    }

    private void assertNotNull(Object object) {
        org.junit.jupiter.api.Assertions.assertNotNull(object);
    }

    private void assertFalse(boolean condition) {
        org.junit.jupiter.api.Assertions.assertFalse(condition);
    }
}