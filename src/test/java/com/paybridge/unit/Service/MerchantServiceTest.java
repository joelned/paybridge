package com.paybridge.unit.Service;

import com.paybridge.Models.DTOs.MerchantRegistrationRequest;
import com.paybridge.Models.DTOs.MerchantRegistrationResponse;
import com.paybridge.Models.Entities.Merchant;
import com.paybridge.Models.Entities.Users;
import com.paybridge.Models.Enums.MerchantStatus;
import com.paybridge.Models.Enums.UserType;
import com.paybridge.Repositories.MerchantRepository;
import com.paybridge.Repositories.UserRepository;
import com.paybridge.Services.EmailService;
import com.paybridge.Services.MerchantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MerchantServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private MerchantService merchantService;

    private MerchantRegistrationRequest validRequest;
    private final String validEmail = "merchant@example.com";
    private final String validPassword = "password123";
    private final String encodedPassword = "encodedPassword123";
    private final String verificationCode = "123456";
    private final String businessName = "Test Business";

    @BeforeEach
    void setUp() {
        // Setup valid registration request
        validRequest = new MerchantRegistrationRequest();
        validRequest.setEmail(validEmail);
        validRequest.setPassword(validPassword);
        validRequest.setBusinessName(businessName);
        validRequest.setBusinessType("ECOMMERCE");
        validRequest.setBusinessCountry("US");
        validRequest.setWebsiteUrl("https://example.com");

        // Manually inject the emailService since it's not in the constructor
        // This is needed because @InjectMocks only uses constructor injection
        merchantService = new MerchantService(userRepository, merchantRepository, passwordEncoder);

        // Use reflection to set the emailService field
        try {
            var emailServiceField = MerchantService.class.getDeclaredField("emailService");
            emailServiceField.setAccessible(true);
            emailServiceField.set(merchantService, emailService);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject emailService", e);
        }
    }

    // Test Case 1: Successful merchant registration
    @Test
    void registerMerchant_Success() {
        // Arrange
        when(merchantRepository.existsByEmail(validEmail)).thenReturn(false);
        when(passwordEncoder.encode(validPassword)).thenReturn(encodedPassword);
        when(userRepository.save(any(Users.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(merchantRepository.save(any(Merchant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        MerchantRegistrationResponse response = merchantService.registerMerchant(validRequest);

        // Assert
        assertNotNull(response);
        assertEquals(businessName, response.getBusinessName());
        assertEquals(validEmail, response.getEmail());
        assertEquals(MerchantStatus.PENDING_PROVIDER_SETUP, response.getStatus());
        assertTrue(response.getMessage().contains("Registration successful"));

        verify(merchantRepository, times(1)).existsByEmail(validEmail);
        verify(passwordEncoder, times(1)).encode(validPassword);
        verify(userRepository, times(1)).save(any(Users.class));
        verify(merchantRepository, times(1)).save(any(Merchant.class));
        verify(emailService, times(1)).sendVerificationEmail(eq(validEmail), anyString(), eq(businessName));
    }

    // Test Case 2: Merchant already exists
    @Test
    void registerMerchant_MerchantAlreadyExists_ThrowsException() {
        // Arrange
        when(merchantRepository.existsByEmail(validEmail)).thenReturn(true);

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> merchantService.registerMerchant(validRequest));

        assertEquals("Merchant already exists", exception.getMessage());
        verify(merchantRepository, times(1)).existsByEmail(validEmail);
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any(Users.class));
        verify(merchantRepository, never()).save(any(Merchant.class));
        verify(emailService, never()).sendVerificationEmail(anyString(), anyString(), anyString());
    }

    @Test
    void registerMerchant_NullRequest_ThrowsException() {
        // Act & Assert
        assertThrows(NullPointerException.class,
                () -> merchantService.registerMerchant(null));
    }


    @Test
    void registerMerchant_VeryLongEmail_Success() {
        // Arrange
        String longEmail = "a".repeat(100) + "@example.com";
        validRequest.setEmail(longEmail);

        when(merchantRepository.existsByEmail(longEmail)).thenReturn(false);
        when(passwordEncoder.encode(validPassword)).thenReturn(encodedPassword);
        when(userRepository.save(any(Users.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(merchantRepository.save(any(Merchant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        MerchantRegistrationResponse response = merchantService.registerMerchant(validRequest);

        // Assert
        assertNotNull(response);
        assertEquals(longEmail, response.getEmail());
        verify(merchantRepository, times(1)).existsByEmail(longEmail);
    }

    @Test
    void registerMerchant_VeryLongBusinessName_Success() {
        // Arrange
        String longBusinessName = "A".repeat(255);
        validRequest.setBusinessName(longBusinessName);

        when(merchantRepository.existsByEmail(validEmail)).thenReturn(false);
        when(passwordEncoder.encode(validPassword)).thenReturn(encodedPassword);
        when(userRepository.save(any(Users.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(merchantRepository.save(any(Merchant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        MerchantRegistrationResponse response = merchantService.registerMerchant(validRequest);

        // Assert
        assertNotNull(response);
        assertEquals(longBusinessName, response.getBusinessName());
    }

    @Test
    void registerMerchant_SpecialCharactersInBusinessName_Success() {
        // Arrange
        String specialBusinessName = "Test & Company < > \" ' @ # $ %";
        validRequest.setBusinessName(specialBusinessName);

        when(merchantRepository.existsByEmail(validEmail)).thenReturn(false);
        when(passwordEncoder.encode(validPassword)).thenReturn(encodedPassword);
        when(userRepository.save(any(Users.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(merchantRepository.save(any(Merchant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        MerchantRegistrationResponse response = merchantService.registerMerchant(validRequest);

        // Assert
        assertNotNull(response);
        assertEquals(specialBusinessName, response.getBusinessName());
    }

    @Test
    void registerMerchant_NullBusinessType_Success() {
        // Arrange
        validRequest.setBusinessType(null);

        when(merchantRepository.existsByEmail(validEmail)).thenReturn(false);
        when(passwordEncoder.encode(validPassword)).thenReturn(encodedPassword);
        when(userRepository.save(any(Users.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(merchantRepository.save(any(Merchant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        MerchantRegistrationResponse response = merchantService.registerMerchant(validRequest);

        // Assert
        assertNotNull(response);
        assertEquals(businessName, response.getBusinessName());
    }

    @Test
    void registerMerchant_NullBusinessCountry_Success() {
        // Arrange
        validRequest.setBusinessCountry(null);

        when(merchantRepository.existsByEmail(validEmail)).thenReturn(false);
        when(passwordEncoder.encode(validPassword)).thenReturn(encodedPassword);
        when(userRepository.save(any(Users.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(merchantRepository.save(any(Merchant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        MerchantRegistrationResponse response = merchantService.registerMerchant(validRequest);

        // Assert
        assertNotNull(response);
        assertEquals(businessName, response.getBusinessName());
    }

    @Test
    void registerMerchant_NullWebsiteUrl_Success() {
        // Arrange
        validRequest.setWebsiteUrl(null);

        when(merchantRepository.existsByEmail(validEmail)).thenReturn(false);
        when(passwordEncoder.encode(validPassword)).thenReturn(encodedPassword);
        when(userRepository.save(any(Users.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(merchantRepository.save(any(Merchant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        MerchantRegistrationResponse response = merchantService.registerMerchant(validRequest);

        // Assert
        assertNotNull(response);
        assertEquals(businessName, response.getBusinessName());
    }

    @Test
    void registerMerchant_InvalidWebsiteUrl_Success() {
        // Arrange
        validRequest.setWebsiteUrl("invalid-url");

        when(merchantRepository.existsByEmail(validEmail)).thenReturn(false);
        when(passwordEncoder.encode(validPassword)).thenReturn(encodedPassword);
        when(userRepository.save(any(Users.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(merchantRepository.save(any(Merchant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        MerchantRegistrationResponse response = merchantService.registerMerchant(validRequest);

        // Assert
        assertNotNull(response);
        assertEquals(businessName, response.getBusinessName());
    }

    @Test
    void registerMerchant_UserRepositoryThrowsException_TransactionFails() {
        // Arrange
        when(merchantRepository.existsByEmail(validEmail)).thenReturn(false);
        when(passwordEncoder.encode(validPassword)).thenReturn(encodedPassword);
        when(userRepository.save(any(Users.class))).thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        assertThrows(RuntimeException.class,
                () -> merchantService.registerMerchant(validRequest));

        verify(merchantRepository, times(1)).existsByEmail(validEmail);
        verify(passwordEncoder, times(1)).encode(validPassword);
        verify(userRepository, times(1)).save(any(Users.class));
        verify(merchantRepository, never()).save(any(Merchant.class));
        verify(emailService, never()).sendVerificationEmail(anyString(), anyString(), anyString());
    }

    @Test
    void registerMerchant_MerchantRepositoryThrowsException_TransactionFails() {
        // Arrange
        when(merchantRepository.existsByEmail(validEmail)).thenReturn(false);
        when(passwordEncoder.encode(validPassword)).thenReturn(encodedPassword);
        when(userRepository.save(any(Users.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(merchantRepository.save(any(Merchant.class))).thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        assertThrows(RuntimeException.class,
                () -> merchantService.registerMerchant(validRequest));

        verify(merchantRepository, times(1)).existsByEmail(validEmail);
        verify(passwordEncoder, times(1)).encode(validPassword);
        verify(userRepository, times(1)).save(any(Users.class));
        verify(merchantRepository, times(1)).save(any(Merchant.class));
        verify(emailService, never()).sendVerificationEmail(anyString(), anyString(), anyString());
    }

    @Test
    void registerMerchant_PasswordEncoderThrowsException_RegistrationFails() {
        // Arrange
        when(merchantRepository.existsByEmail(validEmail)).thenReturn(false);
        when(passwordEncoder.encode(validPassword)).thenThrow(new RuntimeException("Encoding failed"));

        // Act & Assert
        assertThrows(RuntimeException.class,
                () -> merchantService.registerMerchant(validRequest));

        verify(merchantRepository, times(1)).existsByEmail(validEmail);
        verify(passwordEncoder, times(1)).encode(validPassword);
        verify(userRepository, never()).save(any(Users.class));
        verify(merchantRepository, never()).save(any(Merchant.class));
        verify(emailService, never()).sendVerificationEmail(anyString(), anyString(), anyString());
    }

    @Test
    void registerMerchant_DuplicateRegistration_ThrowsException() {
        // Arrange
        when(merchantRepository.existsByEmail(validEmail)).thenReturn(false)
                .thenReturn(true); // Second call returns true

        when(passwordEncoder.encode(validPassword)).thenReturn(encodedPassword);
        when(userRepository.save(any(Users.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(merchantRepository.save(any(Merchant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // First registration should succeed
        MerchantRegistrationResponse firstResponse = merchantService.registerMerchant(validRequest);
        assertNotNull(firstResponse);

        // Reset mocks for second call
        reset(emailService);

        // Second registration should fail
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> merchantService.registerMerchant(validRequest));

        assertEquals("Merchant already exists", exception.getMessage());
    }

    @Test
    void createMerchant_EntityCreation_ValidFields() {
        // Act
        Merchant merchant = merchantService.createMerchant(validRequest);

        // Assert
        assertNotNull(merchant);
        assertEquals(validRequest.getBusinessType(), merchant.getBusinessType());
        assertEquals(validRequest.getBusinessCountry(), merchant.getBusinessCountry());
        assertEquals(validRequest.getBusinessName(), merchant.getBusinessName());
        assertEquals(validRequest.getWebsiteUrl(), merchant.getWebsiteUrl());
        assertEquals(validRequest.getEmail(), merchant.getEmail());
        assertEquals(MerchantStatus.PENDING_PROVIDER_SETUP, merchant.getStatus());
        assertNotNull(merchant.getCreatedAt());
        assertNotNull(merchant.getUpdatedAt());
        assertTrue(merchant.getCreatedAt().isBefore(LocalDateTime.now().plusSeconds(1)));
        assertTrue(merchant.getUpdatedAt().isBefore(LocalDateTime.now().plusSeconds(1)));
    }

    @Test
    void createMerchantUser_EntityCreation_ValidFields() {
        // Arrange
        Merchant merchant = new Merchant();
        merchant.setBusinessName(businessName);
        merchant.setEmail(validEmail);

        when(passwordEncoder.encode(validPassword)).thenReturn(encodedPassword);

        // Act
        Users user = merchantService.createMerchantUser(merchant, validRequest);

        // Assert
        assertNotNull(user);
        assertEquals(merchant, user.getMerchant());
        assertEquals(UserType.MERCHANT, user.getUserType());
        assertEquals(validEmail, user.getEmail());
        assertTrue(user.isEnabled());
        assertEquals(encodedPassword, user.getPassword());
        assertNotNull(user.getVerificationCode());
        assertFalse(user.isEmailVerified());
        assertNotNull(user.getVerificationCodeExpiresAt());
    }

    @Test
    void constructorInjection_WorksCorrectly() {
        // Arrange
        UserRepository userRepo = mock(UserRepository.class);
        MerchantRepository merchantRepo = mock(MerchantRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);

        // Act
        MerchantService service = new MerchantService(userRepo, merchantRepo, encoder);

        // Assert
        assertNotNull(service);
    }


}