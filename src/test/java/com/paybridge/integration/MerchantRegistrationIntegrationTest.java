package com.paybridge.integration;

import com.paybridge.Models.DTOs.MerchantRegistrationRequest;
import com.paybridge.Models.Entities.Merchant;
import com.paybridge.Models.Entities.Users;
import com.paybridge.Models.Enums.MerchantStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


class MerchantRegistrationIntegrationTest extends BaseIntegrationTest {
    @Test
    void registerMerchant_Success_ShouldCreateMerchantAndUser() throws Exception {
        // Arrange
        MerchantRegistrationRequest request = new MerchantRegistrationRequest(
                "Test Business",
                "merchant@example.com",
                "Password123$",
                "ECOMMERCE",
                "US",
                "https://example.com"
        );

        // Act & Assert
        mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.businessName").value("Test Business"))
                .andExpect(jsonPath("$.data.email").value("merchant@example.com"))
                .andExpect(jsonPath("$.data.status").value("PENDING_PROVIDER_SETUP"));

        // Optional: Verify database state
        Merchant savedMerchant = merchantRepository.findByEmail("merchant@example.com");
        assertNotNull(savedMerchant);
        assertEquals("Test Business", savedMerchant.getBusinessName());

        Users savedUser = userRepository.findByEmail("merchant@example.com");
        assertNotNull(savedUser);
        assertFalse(savedUser.isEmailVerified());
        assertNotNull(savedUser.getVerificationCode());
    }

    @Test
    void registerMerchant_DuplicateEmail_ShouldReturnBadRequest() throws Exception {
        // First registration
        MerchantRegistrationRequest firstRequest = new MerchantRegistrationRequest(
                "First Business",
                "duplicate@example.com",
                "Password123$$",
                "ECOMMERCE",
                "US",
                "https://example.com"
        );

        mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(firstRequest)))
                .andExpect(status().isCreated());

        // Second registration with same email
        MerchantRegistrationRequest secondRequest = new MerchantRegistrationRequest(
                "Second Business",
                "duplicate@example.com",
                "Password123$$",
                "SAAS",
                "UK",
                "https://example2.com"
        );

        mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(secondRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Merchant already exists"));
    }

    @Test
    void registerMerchant_InvalidEmail_ShouldReturnValidationError() throws Exception {
        MerchantRegistrationRequest request = new MerchantRegistrationRequest(
                "Test Business",
                "invalid-email",
                "Password123$$",
                "ECOMMERCE",
                "US",
                "https://example.com"
        );

        mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Validation Error"));
    }

    @Test
    void registerMerchant_WeakPassword_ShouldReturnValidationError() throws Exception {
        MerchantRegistrationRequest request = new MerchantRegistrationRequest(
                "Test Business",
                "merchant@example.com",
                "weak",
                "ECOMMERCE",
                "US",
                "https://example.com"
        );

        mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Validation Error"));
    }

    @Test
    void registerMerchant_MissingRequiredFields_ShouldReturnValidationError() throws Exception {
        String invalidJson = "{\"email\":\"test@example.com\"}";

        mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Validation Error"));
    }

    @Test
    void registerMerchant_VerificationCodeGenerated() throws Exception {
        MerchantRegistrationRequest request = new MerchantRegistrationRequest(
                "Test Business",
                "verify@example.com",
                "Password123$",
                "ECOMMERCE",
                "US",
                "https://example.com"
        );

        mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isCreated());

        Users savedUser = userRepository.findByEmail("verify@example.com");
        assertNotNull(savedUser);
        assertNotNull(savedUser.getVerificationCode());
        assertEquals(6, savedUser.getVerificationCode().length());
        assertNotNull(savedUser.getVerificationCodeExpiresAt());
        assertFalse(savedUser.isEmailVerified());
    }
}