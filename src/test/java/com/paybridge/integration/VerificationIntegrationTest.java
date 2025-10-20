package com.paybridge.integration;

import com.paybridge.Models.DTOs.*;
import com.paybridge.Models.Entities.Users;
import com.paybridge.Models.Enums.MerchantStatus;
import com.paybridge.Repositories.MerchantRepository;
import com.paybridge.Repositories.UserRepository;
import com.paybridge.Services.AuthenticationService;
import com.paybridge.Services.EmailService;
import com.paybridge.Services.MerchantService;
import com.paybridge.Services.VerificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Integration test for the complete email verification flow:
 * 1. Register merchant
 * 2. Verify email with code
 * 3. Login successfully
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class VerificationIntegrationTest {

    @Autowired
    private MerchantService merchantService;

    @Autowired
    private VerificationService verificationService;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MerchantRepository merchantRepository;

    @MockitoBean
    private EmailService emailService;

    private MerchantRegistrationRequest registrationRequest;
    private final String testEmail = "integration.test@example.com";
    private final String testPassword = "Test123!@#";
    private final String businessName = "Integration Test Business";

    @BeforeEach
    void setUp() {
        // Clean up any existing test data
        Users existingUser = userRepository.findByEmail(testEmail);
        if (existingUser != null) {
            userRepository.delete(existingUser);
        }

        registrationRequest = new MerchantRegistrationRequest(
                businessName,
                testEmail,
                testPassword,
                "E-commerce",
                "US",
                "https://example.com"
        );

        // Mock email service to prevent actual email sending
        doNothing().when(emailService).sendVerificationEmail(anyString(), anyString(), anyString());
    }

    @AfterEach
    void tearDown() {
        // Clean up test data
        Users user = userRepository.findByEmail(testEmail);
        if (user != null) {
            userRepository.delete(user);
        }
    }

    @Test
    void completeVerificationFlow_Success() {
        // Step 1: Register merchant
        MerchantRegistrationResponse registrationResponse = merchantService.registerMerchant(registrationRequest);

        assertNotNull(registrationResponse);
        assertEquals(businessName, registrationResponse.getBusinessName());
        assertEquals(testEmail, registrationResponse.getEmail());
        assertEquals(MerchantStatus.PENDING_PROVIDER_SETUP, registrationResponse.getStatus());

        // Verify email was sent
        verify(emailService, times(1)).sendVerificationEmail(eq(testEmail), anyString(), eq(businessName));

        // Step 2: Get the verification code from database
        Users user = userRepository.findByEmail(testEmail);
        assertNotNull(user);
        assertFalse(user.isEmailVerified());
        assertNotNull(user.getVerificationCode());
        String verificationCode = user.getVerificationCode();

        // Step 3: Verify email
        VerifyEmailResponse verificationResponse = verificationService.verifyEmail(testEmail, verificationCode);

        assertTrue(verificationResponse.isSuccess());
        assertEquals("Email verified successfully", verificationResponse.getMessage());

        // Verify user is now verified
        user = userRepository.findByEmail(testEmail);
        assertTrue(user.isEmailVerified());
        assertNull(user.getVerificationCode());

        // Step 4: Login
        LoginRequest loginRequest = new LoginRequest(testEmail, testPassword);
        LoginResponse loginResponse = authenticationService.login(loginRequest);

        assertNotNull(loginResponse);
        assertNotNull(loginResponse.getToken());
        assertEquals(testEmail, loginResponse.getEmail());
        assertTrue(loginResponse.getUserType().contains("MERCHANT"));
    }

    @Test
    void loginBeforeVerification_ShouldFail() {
        // Step 1: Register
        merchantService.registerMerchant(registrationRequest);

        // Step 2: Try to login without verifying
        LoginRequest loginRequest = new LoginRequest(testEmail, testPassword);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> authenticationService.login(loginRequest));

        assertEquals("Please verify your email before logging in", exception.getMessage());
    }

    @Test
    void verificationWithInvalidCode_ThenCorrectCode_Success() {
        // Step 1: Register
        merchantService.registerMerchant(registrationRequest);

        Users user = userRepository.findByEmail(testEmail);
        String correctCode = user.getVerificationCode();

        // Step 2: Try with invalid code (3 times)
        for (int i = 0; i < 3; i++) {
            VerifyEmailResponse response = verificationService.verifyEmail(testEmail, "000000");
            assertFalse(response.isSuccess());
            assertEquals("Invalid verification code", response.getMessage());
        }

        // Step 3: Verify with correct code
        VerifyEmailResponse successResponse = verificationService.verifyEmail(testEmail, correctCode);

        assertTrue(successResponse.isSuccess());

        // Step 4: Verify can now login
        LoginRequest loginRequest = new LoginRequest(testEmail, testPassword);
        LoginResponse loginResponse = authenticationService.login(loginRequest);

        assertNotNull(loginResponse);
        assertNotNull(loginResponse.getToken());
    }

    @Test
    void expiredVerificationCode_ThenResend_Success() {
        // Step 1: Register
        merchantService.registerMerchant(registrationRequest);

        // Step 2: Manually expire the code
        Users user = userRepository.findByEmail(testEmail);
        user.setVerificationCodeExpiresAt(LocalDateTime.now().minusMinutes(10));
        userRepository.save(user);

        // Step 3: Try to verify with expired code
        VerifyEmailResponse expiredResponse = verificationService.verifyEmail(
                testEmail,
                user.getVerificationCode()
        );

        assertFalse(expiredResponse.isSuccess());
        assertTrue(expiredResponse.getMessage().contains("expired"));

        // Step 4: Resend verification code
        verificationService.resendVerificationCode(testEmail);

        verify(emailService, times(2)).sendVerificationEmail(eq(testEmail), anyString(), eq(businessName));

        // Step 5: Get new code and verify
        user = userRepository.findByEmail(testEmail);
        String newCode = user.getVerificationCode();

        VerifyEmailResponse successResponse = verificationService.verifyEmail(testEmail, newCode);

        assertTrue(successResponse.isSuccess());
    }

    @Test
    void resendVerificationCode_TooSoon_ShouldFail() {
        // Step 1: Register
        merchantService.registerMerchant(registrationRequest);

        // Step 2: Try to resend immediately (within 60 seconds)
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> verificationService.resendVerificationCode(testEmail));

        assertEquals("Please wait before requesting another verification code", exception.getMessage());

        // Only one email should have been sent (from registration)
        verify(emailService, times(1)).sendVerificationEmail(anyString(), anyString(), anyString());
    }

    @Test
    void resendVerificationCode_After60Seconds_Success() {
        // Step 1: Register
        merchantService.registerMerchant(registrationRequest);

        // Step 2: Manually set last request time to 2 minutes ago
        Users user = userRepository.findByEmail(testEmail);
        user.setLastVerificationRequestAt(LocalDateTime.now().minusMinutes(2));
        userRepository.save(user);

        // Step 3: Resend should now work
        verificationService.resendVerificationCode(testEmail);

        // Two emails should have been sent (registration + resend)
        verify(emailService, times(2)).sendVerificationEmail(eq(testEmail), anyString(), eq(businessName));

        // Verify code was regenerated
        Users updatedUser = userRepository.findByEmail(testEmail);
        assertNotEquals(user.getVerificationCode(), updatedUser.getVerificationCode());
    }

    @Test
    void tooManyVerificationAttempts_ShouldBlock() {
        // Step 1: Register
        merchantService.registerMerchant(registrationRequest);

        // Step 2: Make 5 failed attempts
        for (int i = 0; i < 5; i++) {
            VerifyEmailResponse response = verificationService.verifyEmail(testEmail, "000000");
            assertFalse(response.isSuccess());
        }

        // Step 3: 6th attempt should be blocked
        VerifyEmailResponse blockedResponse = verificationService.verifyEmail(testEmail, "000000");

        assertFalse(blockedResponse.isSuccess());
        assertEquals("Too many verification attempts. Please request a new code.",
                blockedResponse.getMessage());
    }

    @Test
    void verifyAlreadyVerifiedEmail_ShouldReturnMessage() {
        // Step 1: Complete verification
        merchantService.registerMerchant(registrationRequest);
        Users user = userRepository.findByEmail(testEmail);
        String code = user.getVerificationCode();

        verificationService.verifyEmail(testEmail, code);

        // Step 2: Try to verify again
        VerifyEmailResponse secondResponse = verificationService.verifyEmail(testEmail, code);

        assertFalse(secondResponse.isSuccess());
        assertEquals("Email is already verified", secondResponse.getMessage());
    }

    @Test
    void resendToAlreadyVerifiedEmail_ShouldFail() {
        // Step 1: Complete verification
        merchantService.registerMerchant(registrationRequest);
        Users user = userRepository.findByEmail(testEmail);

        verificationService.verifyEmail(testEmail, user.getVerificationCode());

        // Step 2: Manually allow resend time-wise
        user = userRepository.findByEmail(testEmail);
        user.setLastVerificationRequestAt(LocalDateTime.now().minusMinutes(5));
        userRepository.save(user);

        // Step 3: Try to resend
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> verificationService.resendVerificationCode(testEmail));

        assertEquals("Email is already verified", exception.getMessage());
    }

    @Test
    void registerWithExistingEmail_ShouldFail() {
        // Step 1: First registration
        merchantService.registerMerchant(registrationRequest);

        // Step 2: Try to register again with same email
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> merchantService.registerMerchant(registrationRequest));

        assertEquals("Merchant already exists", exception.getMessage());
    }

    @Test
    void loginWithWrongPassword_AfterVerification_ShouldFail() {
        // Step 1: Complete verification
        merchantService.registerMerchant(registrationRequest);
        Users user = userRepository.findByEmail(testEmail);

        verificationService.verifyEmail(testEmail, user.getVerificationCode());

        // Step 2: Try to login with wrong password
        LoginRequest loginRequest = new LoginRequest(testEmail, "WrongPassword123!");

        assertThrows(BadCredentialsException.class,
                () -> authenticationService.login(loginRequest));
    }

    @Test
    void multipleUsers_IndependentVerification() {
        // Create first user
        MerchantRegistrationResponse response1 = merchantService.registerMerchant(registrationRequest);
        assertNotNull(response1);

        // Create second user
        String secondEmail = "second.user@example.com";
        MerchantRegistrationRequest secondRequest = new MerchantRegistrationRequest(
                "Second Business",
                secondEmail,
                "SecondPass123!",
                "SaaS",
                "UK",
                "https://second.com"
        );

        MerchantRegistrationResponse response2 = merchantService.registerMerchant(secondRequest);
        assertNotNull(response2);

        // Verify first user
        Users user1 = userRepository.findByEmail(testEmail);
        verificationService.verifyEmail(testEmail, user1.getVerificationCode());

        // Second user should still be unverified
        Users user2 = userRepository.findByEmail(secondEmail);
        assertFalse(user2.isEmailVerified());

        // First user can login
        LoginRequest login1 = new LoginRequest(testEmail, testPassword);
        LoginResponse loginResponse1 = authenticationService.login(login1);
        assertNotNull(loginResponse1.getToken());

        // Second user cannot login
        LoginRequest login2 = new LoginRequest(secondEmail, "SecondPass123!");
        assertThrows(RuntimeException.class,
                () -> authenticationService.login(login2));

        // Cleanup
        userRepository.delete(user2);
    }

    @Test
    void verificationCodeFormat_ShouldBe6Digits() {
        // Register
        merchantService.registerMerchant(registrationRequest);

        // Get code
        Users user = userRepository.findByEmail(testEmail);
        String code = user.getVerificationCode();

        // Verify format
        assertNotNull(code);
        assertEquals(6, code.length());
        assertTrue(code.matches("\\d{6}"), "Code should be 6 digits");
    }

    @Test
    void verificationCodeExpiration_ShouldBe5Minutes() {
        // Register
        merchantService.registerMerchant(registrationRequest);

        // Get expiration time
        Users user = userRepository.findByEmail(testEmail);
        LocalDateTime expiresAt = user.getVerificationCodeExpiresAt();
        LocalDateTime createdAt = user.getLastVerificationRequestAt();

        assertNotNull(expiresAt);
        assertNotNull(createdAt);

        // Should expire approximately 5 minutes after creation
        long minutesDifference = java.time.Duration.between(createdAt, expiresAt).toMinutes();
        assertEquals(5, minutesDifference);
    }
}