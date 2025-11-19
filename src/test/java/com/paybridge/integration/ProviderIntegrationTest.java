package com.paybridge.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paybridge.Models.DTOs.ProviderConfiguration;
import com.paybridge.Models.Entities.Merchant;
import com.paybridge.Models.Entities.Provider;
import com.paybridge.Models.Entities.Users;
import com.paybridge.Models.Enums.MerchantStatus;
import com.paybridge.Repositories.MerchantRepository;
import com.paybridge.Repositories.ProviderRepository;
import com.paybridge.Repositories.UserRepository;
import com.paybridge.Services.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.servlet.http.Cookie;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ProviderIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private TokenService tokenService;

    private String jwtToken;

    @BeforeEach
    void setUp() {
        // No manual cleanup needed due to @Transactional in BaseIntegrationTest

        // Setup Provider
        Provider provider = new Provider();
        provider.setName("stripe");
        provider.setDisplayName("Stripe");
        providerRepository.save(provider);

        // Setup Merchant
        Merchant merchant = new Merchant();
        merchant.setBusinessName("Provider Test Corp");
        merchant.setEmail("provider@test.com");
        merchant.setBusinessCountry("US");
        merchant.setBusinessType("E_COMMERCE");
        merchant.setStatus(MerchantStatus.PENDING_PROVIDER_SETUP);
        merchant = merchantRepository.save(merchant);

        Users user = new Users();
        user.setEmail("provider@test.com");
        user.setPassword("hashedPass");
        user.setEmailVerified(true);
        user.setMerchant(merchant);
        userRepository.save(user);

        // Generate Token
        jwtToken = tokenService.generateToken(new UsernamePasswordAuthenticationToken(
                "provider@test.com", null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_MERCHANT"))
        ));
    }

    @Test
    void shouldConfigureProviderSuccessfully() throws Exception {
        ProviderConfiguration config = new ProviderConfiguration();
        config.setName("stripe");
        Map<String, Object> credentials = new HashMap<>();
        credentials.put("secretKey", "sk_test_123");
        config.setConfig(credentials);

        // Mock Vault
        doNothing().when(credentialStorageService).saveProviderConfig(any(), any(), any());

        mockMvc.perform(post("/api/v1/providers/configure")
                        .param("testConnection", "false") // Skip connection test to avoid external call
                        .cookie(new Cookie("jwt", jwtToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(config)))
                .andExpect(status().isOk());

        Merchant merchant = merchantRepository.findByEmail("provider@test.com").orElseThrow();
        assertThat(merchant.getStatus()).isEqualTo(MerchantStatus.ACTIVE);
    }
}
