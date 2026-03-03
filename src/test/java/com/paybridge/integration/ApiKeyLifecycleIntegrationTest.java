package com.paybridge.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paybridge.Models.Entities.Merchant;
import com.paybridge.Models.Entities.Users;
import com.paybridge.Models.Enums.MerchantStatus;
import com.paybridge.Models.Enums.UserType;
import com.paybridge.Repositories.MerchantRepository;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiKeyLifecycleIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
        merchant.setBusinessName("Key Merchant");
        merchant.setBusinessCountry("NG");
        merchant.setBusinessType("ECOMMERCE");
        merchant.setEmail("keys@test.com");
        merchant.setStatus(MerchantStatus.ACTIVE);
        merchant = merchantRepository.save(merchant);

        Users user = new Users();
        user.setEmail("keys@test.com");
        user.setPassword("encoded");
        user.setEmailVerified(true);
        user.setEnabled(true);
        user.setUserType(UserType.MERCHANT);
        user.setMerchant(merchant);
        userRepository.save(user);

        jwtToken = tokenService.generateToken(new UsernamePasswordAuthenticationToken(
                "keys@test.com",
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_MERCHANT"))
        ));
    }

    @Test
    void apiKeyLifecycle_GenerateListAndRevoke_WorksEndToEnd() throws Exception {
        String createResponse = mockMvc.perform(post("/api/v1/merchants/api-keys")
                        .cookie(new Cookie("jwt", jwtToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"TEST\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.mode").value("TEST"))
                .andExpect(jsonPath("$.data.key", containsString("pk_test_")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Map<String, Object> parsed = objectMapper.readValue(createResponse, new TypeReference<>() {});
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) parsed.get("data");
        String plainKey = (String) data.get("key");

        String listResponse = mockMvc.perform(get("/api/v1/merchants/api-keys")
                        .cookie(new Cookie("jwt", jwtToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].keyId").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Map<String, Object> parsedList = objectMapper.readValue(listResponse, new TypeReference<>() {});
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> listData = (List<Map<String, Object>>) parsedList.get("data");

        Map<String, Object> testItem = listData.stream()
                .filter(item -> "TEST".equals(item.get("mode")))
                .findFirst()
                .orElseThrow();

        assertThat((Boolean) testItem.get("active")).isTrue();
        assertThat((String) testItem.get("maskedKey")).isNotBlank();
        assertThat((String) testItem.get("maskedKey")).isNotEqualTo(plainKey);

        mockMvc.perform(delete("/api/v1/merchants/api-keys/test")
                        .cookie(new Cookie("jwt", jwtToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("API key revoked successfully"));

        merchant = merchantRepository.findById(merchant.getId()).orElseThrow();
        assertThat(merchant.getApiKeyTest()).isNull();
        assertThat(merchant.getApiKeyTestHash()).isNull();
    }

    @Test
    void revokeApiKey_InvalidKeyId_ReturnsBadRequest() throws Exception {
        mockMvc.perform(delete("/api/v1/merchants/api-keys/unknown")
                        .cookie(new Cookie("jwt", jwtToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("Unsupported API key id")));
    }
}
