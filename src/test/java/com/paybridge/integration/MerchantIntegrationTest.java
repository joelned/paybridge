package com.paybridge.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paybridge.Models.DTOs.LoginRequest;
import com.paybridge.Models.DTOs.MerchantRegistrationRequest;

import com.paybridge.Repositories.MerchantRepository;
import com.paybridge.Repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;

public class MerchantIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockMvc mockMvc;


    @Test
    void shouldRegisterMerchantSuccessfully() throws Exception {
        MerchantRegistrationRequest request = new MerchantRegistrationRequest();
        request.setBusinessName("Integration Test Corp");
        request.setEmail("test@integration.com");
        request.setPassword("SecurePass123$");
        request.setBusinessType("E_COMMERCE");
        request.setBusinessCountry("US");
        request.setWebsiteUrl("https://integration.com");

        mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data", containsString("Registration successful")));

        assertThat(userRepository.findByEmail("test@integration.com")).isNotNull();
    }

    @Test
    void shouldLoginSuccessfullyAfterVerification() throws Exception {
        // 1. Register
        MerchantRegistrationRequest registerRequest = new MerchantRegistrationRequest();
        registerRequest.setBusinessName("Login Corp");
        registerRequest.setEmail("login@test.com");
        registerRequest.setPassword("Pass123$");
        registerRequest.setBusinessType("SAAS");
        registerRequest.setBusinessCountry("US");
        registerRequest.setWebsiteUrl("https://login.com");

        mockMvc.perform(post("/api/v1/merchants")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)));

        // 2. Manually verify user (skipping email flow)
        var user = userRepository.findByEmail("login@test.com");
        user.setEmailVerified(true);
        userRepository.save(user);

        // 3. Login
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("login@test.com");
        loginRequest.setPassword("Pass123$");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("jwt"));
    }
}
