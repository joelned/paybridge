package com.paybridge.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paybridge.Models.Entities.Merchant;
import com.paybridge.Models.Entities.Payment;
import com.paybridge.Models.Entities.Provider;
import com.paybridge.Models.Entities.Users;
import com.paybridge.Models.Enums.MerchantStatus;
import com.paybridge.Models.Enums.PaymentStatus;
import com.paybridge.Models.Enums.UserType;
import com.paybridge.Repositories.MerchantRepository;
import com.paybridge.Repositories.PaymentRepository;
import com.paybridge.Repositories.ProviderRepository;
import com.paybridge.Repositories.UserRepository;
import com.paybridge.Services.TokenService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MerchantAnalyticsIntegrationTest extends BaseIntegrationTest {

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
    private PaymentRepository paymentRepository;

    @Autowired
    private TokenService tokenService;

    private Merchant merchant;
    private String jwtToken;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        userRepository.deleteAll();
        merchantRepository.deleteAll();
        providerRepository.deleteAll();

        merchant = new Merchant();
        merchant.setBusinessName("Analytics Merchant");
        merchant.setBusinessCountry("NG");
        merchant.setBusinessType("ECOMMERCE");
        merchant.setEmail("analytics@test.com");
        merchant.setStatus(MerchantStatus.ACTIVE);
        merchant = merchantRepository.save(merchant);

        Users user = new Users();
        user.setEmail("analytics@test.com");
        user.setPassword("encoded");
        user.setEmailVerified(true);
        user.setEnabled(true);
        user.setUserType(UserType.MERCHANT);
        user.setMerchant(merchant);
        userRepository.save(user);

        jwtToken = tokenService.generateToken(new UsernamePasswordAuthenticationToken(
                "analytics@test.com",
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_MERCHANT"))
        ));

        Provider stripe = new Provider();
        stripe.setName("stripe");
        stripe.setDisplayName("Stripe");
        stripe = providerRepository.save(stripe);

        Provider paystack = new Provider();
        paystack.setName("paystack");
        paystack.setDisplayName("Paystack");
        paystack = providerRepository.save(paystack);

        createPayment(stripe, BigDecimal.valueOf(1000), PaymentStatus.SUCCEEDED, LocalDateTime.now().minusDays(1));
        createPayment(stripe, BigDecimal.valueOf(500), PaymentStatus.FAILED, LocalDateTime.now().minusDays(2));
        createPayment(paystack, BigDecimal.valueOf(2000), PaymentStatus.SUCCEEDED, LocalDateTime.now().minusDays(1));
        createPayment(paystack, BigDecimal.valueOf(700), PaymentStatus.PENDING, LocalDateTime.now());
        createPayment(paystack, BigDecimal.valueOf(300), PaymentStatus.CANCELLED, LocalDateTime.now());
    }

    @Test
    void merchantAnalytics_ReturnsHighLevelMetricsAndProviderBreakdown() throws Exception {
        String content = mockMvc.perform(get("/api/v1/merchants/analytics")
                        .queryParam("days", "30")
                        .cookie(new Cookie("jwt", jwtToken)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Map<String, Object> parsed = objectMapper.readValue(content, new TypeReference<>() {});
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) parsed.get("data");

        assertThat(data.get("totalTransactions")).isEqualTo(5);
        assertThat(data.get("successfulTransactions")).isEqualTo(2);
        assertThat(data.get("failedTransactions")).isEqualTo(2);
        assertThat(data.get("pendingTransactions")).isEqualTo(1);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> providers = (List<Map<String, Object>>) data.get("providers");
        assertThat(providers).hasSize(2);
        assertThat(providers).anyMatch(item -> "stripe".equals(item.get("providerCode")));
        assertThat(providers).anyMatch(item -> "paystack".equals(item.get("providerCode")));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> dailyTrend = (List<Map<String, Object>>) data.get("dailyTrend");
        assertThat(dailyTrend).isNotEmpty();
    }

    private void createPayment(Provider provider, BigDecimal amount, PaymentStatus status, LocalDateTime createdAt) {
        Payment payment = new Payment();
        payment.setMerchant(merchant);
        payment.setProvider(provider);
        payment.setAmount(amount);
        payment.setCurrency("NGN");
        payment.setStatus(status);
        payment.setProviderReference("ref_" + provider.getName() + "_" + System.nanoTime());
        payment.setCreatedAt(createdAt);
        paymentRepository.save(payment);
    }
}

