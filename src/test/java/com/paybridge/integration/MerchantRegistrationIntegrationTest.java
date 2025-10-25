package com.paybridge.integration;

import com.paybridge.Models.DTOs.MerchantRegistrationRequest;
import com.paybridge.Models.DTOs.MerchantRegistrationResponse;
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
                "Password123",
                "ECOMMERCE",
                "US",
                "https://example.com"
        );

        // Act
        MvcResult result = mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.businessName").value("Test Business"))
                .andExpect(jsonPath("$.email").value("merchant@example.com"))
                .andExpect(jsonPath("$.status").value("PENDING_PROVIDER_SETUP"))
                .andReturn();

        // Assert - Verify response
        String responseBody = result.getResponse().getContentAsString();
        MerchantRegistrationResponse response = fromJson(responseBody, MerchantRegistrationResponse.class);

        assertNotNull(response);
        assertEquals("Test Business", response.getBusinessName());
        assertEquals("merchant@example.com", response.getEmail());
        assertEquals(MerchantStatus.PENDING_PROVIDER_SETUP, response.getStatus());

        // Assert - Verify database state
        Merchant savedMerchant = merchantRepository.findByEmail("merchant@example.com");
        assertNotNull(savedMerchant);
        assertEquals("Test Business", savedMerchant.getBusinessName());
        assertEquals("ECOMMERCE", savedMerchant.getBusinessType());

        Users savedUser = userRepository.findByEmail("merchant@example.com");
        assertNotNull(savedUser);
        assertFalse(savedUser.isEmailVerified());
        assertNotNull(savedUser.getVerificationCode());
        assertEquals(savedMerchant.getId(), savedUser.getMerchant().getId());
    }

    @Test
    void registerMerchant_DuplicateEmail_ShouldReturnBadRequest() throws Exception {
        // First registration - should succeed
        MerchantRegistrationRequest firstRequest = new MerchantRegistrationRequest();
        firstRequest.setBusinessName("First Business");
        firstRequest.setEmail("duplicate@example.com");
        firstRequest.setPassword("Password123");
        firstRequest.setBusinessType("ECOMMERCE");
        firstRequest.setBusinessCountry("US");
        firstRequest.setWebsiteUrl("https://example.com");

        mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(firstRequest)))
                .andExpect(status().isCreated());

        // Second registration with same email - should fail
        MerchantRegistrationRequest secondRequest = new MerchantRegistrationRequest();
        secondRequest.setBusinessName("Second Business");
        secondRequest.setEmail("duplicate@example.com"); // Same email
        secondRequest.setPassword("Password123");
        secondRequest.setBusinessType("SAAS");
        secondRequest.setBusinessCountry("UK");
        secondRequest.setWebsiteUrl("https://example2.com");

        mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(secondRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Use a different email"))
                .andExpect(jsonPath("$.error").value("Merchant already exists"));
    }

    @Test
    void registerMerchant_InvalidEmail_ShouldReturnBadRequest() throws Exception {
        // Arrange
        MerchantRegistrationRequest request = new MerchantRegistrationRequest(
                "Test Business",
                "invalid-email",
                "Password123",
                "ECOMMERCE",
                "US",
                "https://example.com"
        );

        // Act & Assert
        mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Error"));
    }

    @Test
    void registerMerchant_WeakPassword_ShouldReturnBadRequest() throws Exception {
        // Arrange
        MerchantRegistrationRequest request = new MerchantRegistrationRequest(
                "Test Business",
                "merchant@example.com",
                "weak",
                "ECOMMERCE",
                "US",
                "https://example.com"
        );

        // Act & Assert
        mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Error"));
    }

    @Test
    void registerMerchant_MissingRequiredFields_ShouldReturnBadRequest() throws Exception {
        // Arrange
        String invalidJson = "{\"email\":\"test@example.com\"}";

        // Act & Assert
        mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerMerchant_EmptyBusinessName_ShouldReturnBadRequest() throws Exception {
        // Arrange
        MerchantRegistrationRequest request = new MerchantRegistrationRequest(
                "",
                "merchant@example.com",
                "Password123",
                "ECOMMERCE",
                "US",
                "https://example.com"
        );

        // Act & Assert
        mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerMerchant_VerificationCodeGenerated() throws Exception {
        // Arrange
        MerchantRegistrationRequest request = new MerchantRegistrationRequest(
                "Test Business",
                "verify@example.com",
                "Password123",
                "ECOMMERCE",
                "US",
                "https://example.com"
        );

        // Act
        mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isCreated());

        // Assert
        Users savedUser = userRepository.findByEmail("verify@example.com");
        assertNotNull(savedUser);
        assertNotNull(savedUser.getVerificationCode());
        assertEquals(6, savedUser.getVerificationCode().length());
        assertNotNull(savedUser.getVerificationCodeExpiresAt());
        assertFalse(savedUser.isEmailVerified());
    }
}