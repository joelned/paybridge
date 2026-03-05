package com.paybridge.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paybridge.Configs.PaymentProvider;
import com.paybridge.Models.DTOs.PaymentProviderResponse;
import com.paybridge.Models.Entities.*;
import com.paybridge.Models.Enums.MerchantStatus;
import com.paybridge.Models.Enums.UserType;
import com.paybridge.Repositories.*;
import com.paybridge.Services.PaymentProviderRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaymentApiRoutingIntegrationTest extends BaseIntegrationTest {

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
    private ProviderConfigRepository providerConfigRepository;

    @MockitoBean
    private PaymentProviderRegistry paymentProviderRegistry;

    private Merchant merchant;

    @BeforeEach
    void setUp() {
        providerConfigRepository.deleteAll();
        userRepository.deleteAll();
        merchantRepository.deleteAll();
        providerRepository.deleteAll();

        merchant = new Merchant();
        merchant.setBusinessName("Routing Merchant");
        merchant.setBusinessType("ECOMMERCE");
        merchant.setBusinessCountry("NG");
        merchant.setEmail("route@test.com");
        merchant.setStatus(MerchantStatus.ACTIVE);
        merchant.setApiKeyTest("pk_test_route_123456789012345");
        merchant.setApiKeyTestHash(sha256("pk_test_route_123456789012345"));
        merchant = merchantRepository.save(merchant);

        Users user = new Users();
        user.setMerchant(merchant);
        user.setEmail("route@test.com");
        user.setPassword("encoded");
        user.setUserType(UserType.MERCHANT);
        user.setEmailVerified(true);
        user.setEnabled(true);
        userRepository.save(user);

        when(credentialStorageService.getProviderConfig(anyString(), eq(merchant.getId())))
                .thenReturn(Map.of("secretKey", "sk_test_123"));
    }

    @Test
    void createPayment_WithExplicitProvider_UsesRequestedProvider() throws Exception {
        Provider paystack = new Provider();
        paystack.setName("paystack");
        paystack.setDisplayName("Paystack");
        paystack = providerRepository.save(paystack);

        ProviderConfig config = new ProviderConfig();
        config.setMerchant(merchant);
        config.setProvider(paystack);
        config.setEnabled(true);
        providerConfigRepository.save(config);

        PaymentProvider provider = mock(PaymentProvider.class);
        PaymentProviderResponse providerResponse = new PaymentProviderResponse();
        providerResponse.setProviderPaymentId("ref_paystack_1");
        providerResponse.setStatus("pending");
        providerResponse.setCheckoutUrl("https://checkout.example.com/pay");

        when(paymentProviderRegistry.getProvider("paystack")).thenReturn(provider);
        when(provider.CreatePaymentRequest(any(), any())).thenReturn(providerResponse);

        Map<String, Object> requestBody = Map.of(
                "amount", 1000,
                "currency", "NGN",
                "description", "Order 1001",
                "email", "customer@example.com",
                "provider", "paystack"
        );

        mockMvc.perform(post("/api/v1/payments")
                        .header("x-api-key", "pk_test_route_123456789012345")
                        .header("Idempotency-Key", "idem-route-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.provider").value("paystack"))
                .andExpect(jsonPath("$.data.providerReference").value("ref_paystack_1"));

        verify(paymentProviderRegistry).getProvider("paystack");
        verify(provider).CreatePaymentRequest(any(), any());
    }

    @Test
    void createPayment_WithoutProviderAndMultipleEnabled_ReturnsBadRequest() throws Exception {
        Provider stripe = new Provider();
        stripe.setName("stripe");
        stripe.setDisplayName("Stripe");
        stripe = providerRepository.save(stripe);

        Provider paystack = new Provider();
        paystack.setName("paystack");
        paystack.setDisplayName("Paystack");
        paystack = providerRepository.save(paystack);

        ProviderConfig c1 = new ProviderConfig();
        c1.setMerchant(merchant);
        c1.setProvider(stripe);
        c1.setEnabled(true);
        providerConfigRepository.save(c1);

        ProviderConfig c2 = new ProviderConfig();
        c2.setMerchant(merchant);
        c2.setProvider(paystack);
        c2.setEnabled(true);
        providerConfigRepository.save(c2);

        Map<String, Object> requestBody = Map.of(
                "amount", 1000,
                "currency", "NGN",
                "description", "Order 1002",
                "email", "customer@example.com"
        );

        mockMvc.perform(post("/api/v1/payments")
                        .header("x-api-key", "pk_test_route_123456789012345")
                        .header("Idempotency-Key", "idem-route-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("Multiple providers are enabled")));

        verify(paymentProviderRegistry, never()).getProvider(anyString());
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
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
