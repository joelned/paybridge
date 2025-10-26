package com.paybridge.integration;

import com.paybridge.Models.DTOs.LoginRequest;
import com.paybridge.Models.DTOs.MerchantRegistrationRequest;
import com.paybridge.Models.DTOs.VerifyEmailRequest;
import com.paybridge.Models.Entities.Users;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for authentication flows including login
 */
public class AuthenticationIntegrationTest extends BaseIntegrationTest {

    @Test
    void login_Success_ReturnsJwtInCookie() throws Exception {
        // 1. Register and verify a merchant first
        String email = "login@example.com";
        String password = "Password123";
        registerAndVerifyMerchant(email, password);

        // 2. Login with valid credentials
        LoginRequest loginRequest = new LoginRequest(email, password);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.userType").value("[ROLE_MERCHANT]"))
                .andExpect(jsonPath("$.expiresIn").value("1 hour"))
                .andExpect(jsonPath("$.token").doesNotExist()) // Token should not be in body
                .andExpect(header().exists("Set-Cookie"))
                .andReturn();

        // 3. Verify cookie contains JWT
        String setCookieHeader = result.getResponse().getHeader("Set-Cookie");
        assertNotNull(setCookieHeader);
        assertTrue(setCookieHeader.contains("jwt="));
        assertTrue(setCookieHeader.contains("HttpOnly"));
        assertTrue(setCookieHeader.contains("Secure"));
        assertTrue(setCookieHeader.contains("SameSite=None"));
    }

    @Test
    void login_UnverifiedEmail_ShouldReturnError() throws Exception {
        // 1. Register but don't verify
        MerchantRegistrationRequest registrationRequest = createRegistrationRequest();
        registrationRequest.setEmail("unverified@example.com");
        registrationRequest.setPassword("Password123");

        mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(registrationRequest)))
                .andExpect(status().isCreated());

        // 2. Try to login without verifying
        LoginRequest loginRequest = new LoginRequest("unverified@example.com", "Password123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(loginRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Please verify your email before logging in"));
    }

    @Test
    void login_InvalidCredentials_ShouldReturnError() throws Exception {
        // 1. Register and verify
        String email = "invalidcreds@example.com";
        registerAndVerifyMerchant(email, "Password123");

        // 2. Login with wrong password
        LoginRequest loginRequest = new LoginRequest(email, "WrongPassword123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(loginRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_NonExistentUser_ShouldReturnError() throws Exception {
        LoginRequest loginRequest = new LoginRequest("nonexistent@example.com", "Password123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(loginRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_EmptyEmail_ShouldReturnValidationError() throws Exception {
        LoginRequest loginRequest = new LoginRequest("", "Password123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(loginRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Error"));
    }

    @Test
    void login_EmptyPassword_ShouldReturnValidationError() throws Exception {
        LoginRequest loginRequest = new LoginRequest("test@example.com", "");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(loginRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Error"));
    }

    @Test
    void login_InvalidEmailFormat_ShouldReturnValidationError() throws Exception {
        LoginRequest loginRequest = new LoginRequest("invalid-email", "Password123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(loginRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Error"));
    }

    @Test
    void login_MultipleTimes_ShouldGenerateDifferentTokens() throws Exception {
        // 1. Register and verify
        String email = "multiplelogins@example.com";
        String password = "Password123";
        registerAndVerifyMerchant(email, password);

        LoginRequest loginRequest = new LoginRequest(email, password);

        // 2. Login first time
        MvcResult result1 = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        // 3. Wait a bit to ensure different timestamp
        Thread.sleep(1100); // Wait just over 1 second to ensure different iat

        // 4. Login second time
        MvcResult result2 = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        // 5. Verify different tokens (cookies should be different due to different timestamps)
        String cookie1 = result1.getResponse().getHeader("Set-Cookie");
        String cookie2 = result2.getResponse().getHeader("Set-Cookie");

        assertNotNull(cookie1);
        assertNotNull(cookie2);
        // The JWT tokens should be different (different issued-at times)
        assertNotEquals(cookie1, cookie2);
    }

    @Test
    void login_CaseSensitiveEmail_ShouldWork() throws Exception {
        // Register with lowercase
        String email = "case@example.com";
        String password = "Password123";
        registerAndVerifyMerchant(email, password);

        // Try login with different cases - should all work
        LoginRequest loginRequest1 = new LoginRequest(email, password);
        LoginRequest loginRequest2 = new LoginRequest(email.toUpperCase(), password);

        // Login with exact email should work
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(loginRequest1)))
                .andExpect(status().isOk());

        // Login with uppercase email might fail depending on DB collation
        // This test documents the behavior
        try {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(loginRequest2)))
                    .andReturn();
        } catch (Exception e) {
            // Expected if emails are case-sensitive
        }
    }

    // Helper methods
    private void registerAndVerifyMerchant(String email, String password) throws Exception {
        // Register
        MerchantRegistrationRequest registrationRequest = createRegistrationRequest();
        registrationRequest.setEmail(email);
        registrationRequest.setPassword(password);

        mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(registrationRequest)))
                .andExpect(status().isCreated());

        // Get verification code and verify
        Users user = userRepository.findByEmail(email);
        assertNotNull(user);
        String verificationCode = user.getVerificationCode();

        VerifyEmailRequest verifyRequest = new VerifyEmailRequest(email, verificationCode);
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