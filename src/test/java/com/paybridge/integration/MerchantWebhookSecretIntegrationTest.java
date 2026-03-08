package com.paybridge.integration;

import com.paybridge.Models.Entities.Merchant;
import com.paybridge.Models.Entities.Users;
import com.paybridge.Models.Enums.MerchantStatus;
import com.paybridge.Models.Enums.UserType;
import com.paybridge.Repositories.MerchantRepository;
import com.paybridge.Repositories.UserRepository;
import com.paybridge.Services.TokenService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MerchantWebhookSecretIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TokenService tokenService;

    private String jwtToken;
    private Merchant merchant;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        merchantRepository.deleteAll();

        merchant = new Merchant();
        merchant.setBusinessName("Webhook Merchant");
        merchant.setBusinessCountry("US");
        merchant.setBusinessType("ECOMMERCE");
        merchant.setEmail("webhooks@test.com");
        merchant.setStatus(MerchantStatus.ACTIVE);
        merchant = merchantRepository.save(merchant);

        Users user = new Users();
        user.setEmail("webhooks@test.com");
        user.setPassword("encoded");
        user.setEmailVerified(true);
        user.setEnabled(true);
        user.setUserType(UserType.MERCHANT);
        user.setMerchant(merchant);
        userRepository.save(user);

        jwtToken = tokenService.generateToken(new UsernamePasswordAuthenticationToken(
                "webhooks@test.com",
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_MERCHANT"))
        ));
    }

    @Test
    void getWebhookSecret_WhenConfigured_ReturnsMaskedSecret() throws Exception {
        when(credentialStorageService.providerConfigExists("stripe", merchant.getId())).thenReturn(true);
        when(credentialStorageService.getProviderConfig("stripe", merchant.getId()))
                .thenReturn(Map.of("webhookSecret", "whsec_1234567890abcdef"));

        mockMvc.perform(get("/api/v1/merchants/webhooks/stripe")
                        .cookie(new Cookie("jwt", jwtToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.provider").value("stripe"))
                .andExpect(jsonPath("$.data.configured").value(true))
                .andExpect(jsonPath("$.data.maskedSecret", containsString("****")));
    }

    @Test
    void putWebhookSecret_WhenProviderConfigured_StoresSecret() throws Exception {
        when(credentialStorageService.providerConfigExists("stripe", merchant.getId())).thenReturn(true);
        when(credentialStorageService.getProviderConfig("stripe", merchant.getId()))
                .thenReturn(Map.of("webhookSecret", "whsec_new_secret_abc123"));

        mockMvc.perform(put("/api/v1/merchants/webhooks/stripe/secret")
                        .cookie(new Cookie("jwt", jwtToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"secret\":\"whsec_new_secret_abc123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.provider").value("stripe"))
                .andExpect(jsonPath("$.data.configured").value(true));

        verify(credentialStorageService).updateProviderConfigProperty(
                eq("stripe"),
                eq(merchant.getId()),
                eq("webhookSecret"),
                eq("whsec_new_secret_abc123")
        );
    }

    @Test
    void rotateWebhookSecret_UnsupportedProvider_ReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/merchants/webhooks/flutterwave/rotate")
                        .cookie(new Cookie("jwt", jwtToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"secret\":\"abc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message", containsString("Unsupported provider")));
    }
}
