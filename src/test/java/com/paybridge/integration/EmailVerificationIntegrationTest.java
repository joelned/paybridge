package com.paybridge.integration;

import com.paybridge.Models.DTOs.MerchantRegistrationRequest;
import com.paybridge.Models.DTOs.VerifyEmailRequest;
import com.paybridge.Models.DTOs.ResendVerificationRequest;
import com.paybridge.Models.Entities.Users;
import com.paybridge.Repositories.UserRepository;
import com.paybridge.Services.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class EmailVerificationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        doNothing().when(emailService).sendVerificationEmail(anyString(), anyString(), anyString());
    }


    @Test
    void verifyEmail_ValidCode_ShouldVerifyEmailAndGenerateApiKeys() throws Exception {
        // 1. Register a merchant first
        MerchantRegistrationRequest registrationRequest = createRegistrationRequest();
        mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(registrationRequest)))
                .andExpect(status().isCreated());

        // 2. Get the verification code from the database
        Users user = userRepository.findByEmail(registrationRequest.getEmail());
        assertNotNull(user);
        String verificationCode = user.getVerificationCode();
        assertNotNull(verificationCode);

        // 3. Verify email with the valid code
        VerifyEmailRequest verifyRequest = new VerifyEmailRequest();
        verifyRequest.setEmail(registrationRequest.getEmail());
        verifyRequest.setCode(verificationCode);

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(verifyRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Email verified successfully"));

        // 4. Verify user is marked as verified in database
        Users verifiedUser = userRepository.findByEmail(registrationRequest.getEmail());
        assertTrue(verifiedUser.isEmailVerified());
        assertNull(verifiedUser.getVerificationCode());
        assertNull(verifiedUser.getVerificationCodeExpiresAt());
        assertEquals(0, verifiedUser.getVerificationAttempts());

        // 5. Verify merchant has API keys generated
        assertNotNull(verifiedUser.getMerchant());
        assertNotNull(verifiedUser.getMerchant().getApiKeyTest());
        assertNotNull(verifiedUser.getMerchant().getApiKeyLive());
        assertTrue(verifiedUser.getMerchant().getApiKeyTest().startsWith("pk_test_"));
        assertTrue(verifiedUser.getMerchant().getApiKeyLive().startsWith("pk_live_"));
        assertTrue(verifiedUser.getMerchant().isTestMode());
    }

    @Test
    void verifyEmail_InvalidCode_ShouldReturnError() throws Exception {
        // 1. Register a merchant
        MerchantRegistrationRequest registrationRequest = createRegistrationRequest();
        registrationRequest.setEmail("invalidcode@example.com");
        mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(registrationRequest)))
                .andExpect(status().isCreated());

        // 2. Try to verify with invalid code
        VerifyEmailRequest verifyRequest = new VerifyEmailRequest();
        verifyRequest.setEmail("invalidcode@example.com");
        verifyRequest.setCode("000000"); // Invalid code

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(verifyRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid verification code"));

        // 3. Verify user is NOT verified and attempts incremented
        Users user = userRepository.findByEmail("invalidcode@example.com");
        assertFalse(user.isEmailVerified());
        assertEquals(1, user.getVerificationAttempts());
        assertNotNull(user.getVerificationCode()); // Original code still there
    }

    @Test
    void verifyEmail_ExpiredCode_ShouldReturnError() throws Exception {
        // 1. Register a merchant
        MerchantRegistrationRequest registrationRequest = createRegistrationRequest();
        registrationRequest.setEmail("expired@example.com");
        mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(registrationRequest)))
                .andExpect(status().isCreated());

        // 2. Manually set the code as expired in database
        Users user = userRepository.findByEmail("expired@example.com");
        user.setVerificationCodeExpiresAt(LocalDateTime.now().minusMinutes(1)); // Expired 1 minute ago
        userRepository.save(user);

        // 3. Try to verify with expired code
        VerifyEmailRequest verifyRequest = new VerifyEmailRequest();
        verifyRequest.setEmail("expired@example.com");
        verifyRequest.setCode(user.getVerificationCode());

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(verifyRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Verification code has expired. Please request a new one."));
    }

    @Test
    void verifyEmail_AlreadyVerified_ShouldReturnError() throws Exception {
        // 1. Register and verify a merchant first
        MerchantRegistrationRequest registrationRequest = createRegistrationRequest();
        registrationRequest.setEmail("alreadyverified@example.com");

        // Register
        mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(registrationRequest)))
                .andExpect(status().isCreated());

        // Get code and verify
        Users user = userRepository.findByEmail("alreadyverified@example.com");
        VerifyEmailRequest verifyRequest = new VerifyEmailRequest();
        verifyRequest.setEmail("alreadyverified@example.com");
        verifyRequest.setCode(user.getVerificationCode());

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(verifyRequest)))
                .andExpect(status().isOk());

        // 2. Try to verify again with the same code
        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(verifyRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Email is already verified"));
    }

    @Test
    void verifyEmail_TooManyAttempts_ShouldReturnError() throws Exception {
        // 1. Register a merchant
        MerchantRegistrationRequest registrationRequest = createRegistrationRequest();
        registrationRequest.setEmail("attempts@example.com");
        mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(registrationRequest)))
                .andExpect(status().isCreated());

        // 2. Manually set max attempts in database
        Users user = userRepository.findByEmail("attempts@example.com");
        user.setVerificationAttempts(5); // Max attempts reached
        userRepository.save(user);

        // 3. Try to verify (should fail due to max attempts)
        VerifyEmailRequest verifyRequest = new VerifyEmailRequest();
        verifyRequest.setEmail("attempts@example.com");
        verifyRequest.setCode("123456");

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(verifyRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Too many verification attempts. Please request a new code."));
    }

    @Test
    void verifyEmail_NonExistentEmail_ShouldReturnError() throws Exception {
        // Try to verify with non-existent email
        VerifyEmailRequest verifyRequest = new VerifyEmailRequest();
        verifyRequest.setEmail("nonexistent@example.com");
        verifyRequest.setCode("123456");

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(verifyRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("No account found with this email"));
    }

    @Test
    void resendVerificationCode_Success_ShouldSendNewCode() throws Exception {
        // First, register a merchant (this will create a user)
        MerchantRegistrationRequest registrationRequest = new MerchantRegistrationRequest();
        registrationRequest.setBusinessName("Test Business");
        registrationRequest.setEmail("resend@example.com");
        registrationRequest.setPassword("Password123$");
        registrationRequest.setBusinessType("ECOMMERCE");
        registrationRequest.setBusinessCountry("US");
        registrationRequest.setWebsiteUrl("https://example.com");

        mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(registrationRequest)))
                .andExpect(status().isCreated());

        // Verify email was sent during registration
        verify(emailService, times(1)).sendVerificationEmail(eq("resend@example.com"), anyString(), anyString());

        // Manually update the user to bypass the cooldown
        Users user = userRepository.findByEmail("resend@example.com");
        if (user != null) {
            // Set last verification request to be old enough to bypass cooldown
            user.setLastVerificationRequestAt(java.time.LocalDateTime.now().minusMinutes(6));
            userRepository.save(user);
        }

        // Now resend verification code
        ResendVerificationRequest resendRequest = new ResendVerificationRequest();
        resendRequest.setEmail("resend@example.com");

        mockMvc.perform(post("/api/v1/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(resendRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Verification code sent successfully"));

        // Verify email was sent again
        verify(emailService, times(2)).sendVerificationEmail(eq("resend@example.com"), anyString(), anyString());
    }

    @Test
    void resendVerificationCode_AlreadyVerified_ShouldReturnError() throws Exception {
        // 1. Register and verify a merchant
        MerchantRegistrationRequest registrationRequest = createRegistrationRequest();
        registrationRequest.setEmail("resendverified@example.com");

        // Register and verify
        mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(registrationRequest)))
                .andExpect(status().isCreated());

        Users user = userRepository.findByEmail("resendverified@example.com");
        VerifyEmailRequest verifyRequest = new VerifyEmailRequest();
        verifyRequest.setEmail("resendverified@example.com");
        verifyRequest.setCode(user.getVerificationCode());
        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(verifyRequest)))
                .andExpect(status().isOk());

        // 2. Try to resend verification for already verified email
        ResendVerificationRequest resendRequest = new ResendVerificationRequest();
        resendRequest.setEmail("resendverified@example.com");

        mockMvc.perform(post("/api/v1/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(resendRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Email is already verified"));
    }

    @Test
    void resendVerificationCode_TooSoon_ShouldReturnError() throws Exception {
        // 1. Register a merchant
        MerchantRegistrationRequest registrationRequest = createRegistrationRequest();
        registrationRequest.setEmail("toosoon@example.com");
        mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(registrationRequest)))
                .andExpect(status().isCreated());

        // 2. Set last request time to recent (within 1 minute)
        Users user = userRepository.findByEmail("toosoon@example.com");
        user.setLastVerificationRequestAt(LocalDateTime.now().minusSeconds(30)); // 30 seconds ago
        userRepository.save(user);

        // 3. Try to resend too soon
        ResendVerificationRequest resendRequest = new ResendVerificationRequest();
        resendRequest.setEmail("toosoon@example.com");

        mockMvc.perform(post("/api/v1/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(resendRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Please wait before requesting another verification code"));
    }

    @Test
    void resendVerificationCode_NonExistentEmail_ShouldReturnError() throws Exception {
        // Try to resend for non-existent email
        ResendVerificationRequest resendRequest = new ResendVerificationRequest();
        resendRequest.setEmail("nonexistent@example.com");

        mockMvc.perform(post("/api/v1/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(resendRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("No account found with this email"));
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