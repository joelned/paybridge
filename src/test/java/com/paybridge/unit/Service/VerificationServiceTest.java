package com.paybridge.unit.Service;

import com.paybridge.Models.DTOs.ApiResponse;
import com.paybridge.Models.Entities.Merchant;
import com.paybridge.Models.Entities.Users;
import com.paybridge.Models.Enums.UserType;
import com.paybridge.Repositories.MerchantRepository;
import com.paybridge.Repositories.UserRepository;
import com.paybridge.Services.EmailProvider;
import com.paybridge.Services.VerificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VerificationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailProvider emailProvider;

    @Mock
    private MerchantRepository merchantRepository;

    @InjectMocks
    private VerificationService verificationService;

    private Users unverifiedUser;
    private Users verifiedUser;
    private Merchant merchant;
    private final String validEmail = "test@example.com";
    private final String validCode = "123456";
    private final String invalidCode = "000000";

    @BeforeEach
    void setUp() {
        // Setup merchant
        merchant = new Merchant();
        merchant.setId(10L);
        merchant.setBusinessName("Test Business");

        // Setup unverified user
        unverifiedUser = new Users();
        unverifiedUser.setId(1L);
        unverifiedUser.setEmail(validEmail);
        unverifiedUser.setEmailVerified(false);
        unverifiedUser.setUserType(UserType.MERCHANT);
        unverifiedUser.setMerchant(merchant);
        unverifiedUser.setVerificationCode(sha256(validCode));
        unverifiedUser.setVerificationCodeExpiresAt(LocalDateTime.now().plusMinutes(15));
        unverifiedUser.setVerificationAttempts(0);

        verifiedUser = new Users();
        verifiedUser.setId(2L);
        verifiedUser.setEmail("verified@example.com");
        verifiedUser.setEmailVerified(true);
        verifiedUser.setUserType(UserType.MERCHANT);
    }

    @Test
    void verifyEmail_Success() {
        // Arrange
        when(userRepository.findByEmail(validEmail)).thenReturn(unverifiedUser);
        when(userRepository.save(any(Users.class))).thenReturn(unverifiedUser);
        when(merchantRepository.save(any(Merchant.class))).thenReturn(merchant);

        // Act
        ApiResponse<String> response = verificationService.verifyEmail(validEmail, validCode);

        // Assert
        assertTrue(response.isSuccess());
        assertEquals("Email verified successfully", response.getData());
        verify(userRepository, times(1)).save(unverifiedUser);
        verify(merchantRepository, times(1)).save(merchant);
        assertTrue(unverifiedUser.isEmailVerified());
        assertNull(unverifiedUser.getVerificationCode());
    }

    @Test
    void verifyEmail_UserNotFound() {
        // Arrange
        when(userRepository.findByEmail(validEmail)).thenReturn(null);

        // Act
        ApiResponse<String> response = verificationService.verifyEmail(validEmail, validCode);

        // Assert
        assertFalse(response.isSuccess());
        assertEquals("Invalid or expired verification code", response.getError().getMessage());
        verify(userRepository, never()).save(any(Users.class));
    }

    @Test
    void verifyEmail_AlreadyVerified() {
        // Arrange
        when(userRepository.findByEmail("verified@example.com")).thenReturn(verifiedUser);

        // Act
        ApiResponse<String> response = verificationService.verifyEmail("verified@example.com", validCode);

        // Assert
        assertFalse(response.isSuccess());
        assertEquals("Invalid or expired verification code", response.getError().getMessage());
        verify(userRepository, never()).save(any(Users.class));
    }

    @Test
    void verifyEmail_TooManyAttempts() {
        // Arrange
        unverifiedUser.setVerificationAttempts(5);
        when(userRepository.findByEmail(validEmail)).thenReturn(unverifiedUser);

        // Act
        ApiResponse<String> response = verificationService.verifyEmail(validEmail, validCode);

        // Assert
        assertFalse(response.isSuccess());
        assertEquals("Invalid or expired verification code", response.getError().getMessage());
        verify(userRepository, never()).save(any(Users.class));
    }

    @Test
    void verifyEmail_InvalidCode() {
        // Arrange
        when(userRepository.findByEmail(validEmail)).thenReturn(unverifiedUser);
        when(userRepository.save(any(Users.class))).thenReturn(unverifiedUser);

        // Act
        ApiResponse<String> response = verificationService.verifyEmail(validEmail, invalidCode);

        // Assert
        assertFalse(response.isSuccess());
        assertEquals("Invalid verification code", response.getError().getMessage());
        verify(userRepository, times(1)).save(unverifiedUser);
        assertEquals(1, unverifiedUser.getVerificationAttempts());
    }

    @Test
    void verifyEmail_ExpiredCode() {
        // Arrange
        unverifiedUser.setVerificationCodeExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(userRepository.findByEmail(validEmail)).thenReturn(unverifiedUser);
        when(userRepository.save(any(Users.class))).thenReturn(unverifiedUser);

        // Act
        ApiResponse<String> response = verificationService.verifyEmail(validEmail, validCode);

        // Assert
        assertFalse(response.isSuccess());
        assertEquals("Verification code expired. Please request a new one", response.getError().getMessage());
        verify(userRepository, times(1)).save(unverifiedUser);
    }

    @Test
    void verifyEmail_NullCode() {
        // Arrange
        unverifiedUser.setVerificationCode(null);
        when(userRepository.findByEmail(validEmail)).thenReturn(unverifiedUser);

        // Act
        ApiResponse<String> response = verificationService.verifyEmail(validEmail, validCode);

        // Assert
        assertFalse(response.isSuccess());
        assertEquals("Invalid verification code", response.getError().getMessage());
    }

    @Test
    void resendVerificationCode_Success() {
        // Arrange
        when(userRepository.findByEmail(validEmail)).thenReturn(unverifiedUser);
        when(userRepository.save(any(Users.class))).thenReturn(unverifiedUser);

        // Act
        verificationService.resendVerificationCode(validEmail);

        // Assert
        verify(userRepository, times(1)).save(unverifiedUser);
        verify(emailProvider, times(1)).sendVerificationEmail(
                eq(validEmail),
                anyString(), // new verification code
                eq("Test Business")
        );
        assertNotNull(unverifiedUser.getVerificationCode());
        assertNotNull(unverifiedUser.getVerificationCodeExpiresAt());
    }

    @Test
    void resendVerificationCode_UserNotFound() {
        // Arrange
        when(userRepository.findByEmail(validEmail)).thenReturn(null);

        // Act & Assert
        assertDoesNotThrow(() -> verificationService.resendVerificationCode(validEmail));
        verify(userRepository, never()).save(any(Users.class));
        verify(emailProvider, never()).sendVerificationEmail(anyString(), anyString(), anyString());
    }

    @Test
    void resendVerificationCode_AlreadyVerified() {
        // Arrange
        when(userRepository.findByEmail("verified@example.com")).thenReturn(verifiedUser);

        // Act & Assert
        assertDoesNotThrow(() -> verificationService.resendVerificationCode("verified@example.com"));
        verify(userRepository, never()).save(any(Users.class));
        verify(emailProvider, never()).sendVerificationEmail(anyString(), anyString(), anyString());
    }

    @Test
    void resendVerificationCode_TooSoon() {
        // Arrange
        unverifiedUser.setLastVerificationRequestAt(LocalDateTime.now().minusSeconds(30)); // 30 seconds ago
        when(userRepository.findByEmail(validEmail)).thenReturn(unverifiedUser);

        // Act & Assert
        assertDoesNotThrow(() -> verificationService.resendVerificationCode(validEmail));
        verify(userRepository, never()).save(any(Users.class));
        verify(emailProvider, never()).sendVerificationEmail(anyString(), anyString(), anyString());
    }

    @Test
    void resendVerificationCode_UserWithoutMerchant() {
        // Arrange
        Users adminUser = new Users();
        adminUser.setId(3L);
        adminUser.setEmail("admin@example.com");
        adminUser.setEmailVerified(false);
        adminUser.setUserType(UserType.ADMIN);
        adminUser.setMerchant(null); // No merchant for admin

        when(userRepository.findByEmail("admin@example.com")).thenReturn(adminUser);
        when(userRepository.save(any(Users.class))).thenReturn(adminUser);

        // Act
        verificationService.resendVerificationCode("admin@example.com");

        // Assert
        verify(emailProvider, times(1)).sendVerificationEmail(
                eq("admin@example.com"),
                anyString(),
                eq(null) // businessName should be null
        );
    }

    @Test
    void verifyEmail_MultipleFailedAttemptsThenSuccess() {
        // Arrange
        when(userRepository.findByEmail(validEmail)).thenReturn(unverifiedUser);
        when(userRepository.save(any(Users.class))).thenReturn(unverifiedUser);
        when(merchantRepository.save(any(Merchant.class))).thenReturn(merchant);

        // Act - First 4 failed attempts
        for (int i = 0; i < 4; i++) {
            ApiResponse<String> response = verificationService.verifyEmail(validEmail, invalidCode);
            assertFalse(response.isSuccess());
            assertEquals("Invalid verification code", response.getError().getMessage());
        }

        // Verify attempts count
        assertEquals(4, unverifiedUser.getVerificationAttempts());

        // Act - Fifth attempt with correct code should succeed
        ApiResponse<String> successResponse = verificationService.verifyEmail(validEmail, validCode);

        // Assert
        assertTrue(successResponse.isSuccess());
        assertEquals("Email verified successfully", successResponse.getData());
        assertTrue(unverifiedUser.isEmailVerified());
    }

    @Test
    void verifyEmail_ExactlyFiveAttemptsBlocks() {
        // Arrange
        unverifiedUser.setVerificationAttempts(5); // User already has 5 failed attempts
        when(userRepository.findByEmail(validEmail)).thenReturn(unverifiedUser);
        // Note: No save() should be called because we block before saving

        // Act - This would be the 6th attempt, but it should be blocked
        ApiResponse<String> response = verificationService.verifyEmail(validEmail, invalidCode);

        // Assert
        assertFalse(response.isSuccess());
        assertEquals("Invalid or expired verification code", response.getError().getMessage());

        // Verify that save was NOT called because we blocked early
        verify(userRepository, never()).save(any(Users.class));
        // The attempt count should remain at 5, not increment to 6
        assertEquals(5, unverifiedUser.getVerificationAttempts());
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
