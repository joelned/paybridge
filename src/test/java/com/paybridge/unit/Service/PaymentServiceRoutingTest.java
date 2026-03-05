package com.paybridge.Services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paybridge.Configs.PaymentProvider;
import com.paybridge.Models.DTOs.CreatePaymentRequest;
import com.paybridge.Models.DTOs.PaymentProviderResponse;
import com.paybridge.Models.DTOs.PaymentResponse;
import com.paybridge.Models.Entities.IdempotencyKey;
import com.paybridge.Models.Entities.Merchant;
import com.paybridge.Models.Entities.Provider;
import com.paybridge.Models.Entities.ProviderConfig;
import com.paybridge.Repositories.ProviderConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceRoutingTest {

    @Mock
    private ProviderConfigRepository providerConfigRepository;
    @Mock
    private PaymentProviderRegistry paymentProviderRegistry;
    @Mock
    private CredentialStorageService credentialStorageService;
    @Mock
    private PaymentTransactionHelper transactionHelper;
    @Mock
    private PaymentProvider paymentProvider;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                providerConfigRepository,
                paymentProviderRegistry,
                credentialStorageService,
                new ObjectMapper().findAndRegisterModules(),
                transactionHelper
        );
    }

    @Test
    void createPayment_UsesExplicitRequestedProvider_AndFinalizesSuccessfully() {
        Merchant merchant = new Merchant();
        merchant.setId(99L);

        IdempotencyKey idempotencyRecord = new IdempotencyKey();

        Provider provider = new Provider();
        provider.setName("paystack");

        ProviderConfig providerConfig = new ProviderConfig();
        providerConfig.setProvider(provider);

        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setAmount(new BigDecimal("1000.00"));
        request.setCurrency("NGN");
        request.setDescription("Order #1001");
        request.setProvider("paystack");
        request.setEmail("customer@example.com");

        PaymentProviderResponse providerResponse = new PaymentProviderResponse();
        providerResponse.setProviderPaymentId("ref_123");
        providerResponse.setStatus("pending");
        providerResponse.setCheckoutUrl("https://checkout.example.com");

        PaymentResponse expectedResponse = new PaymentResponse();
        expectedResponse.setId(UUID.randomUUID());
        expectedResponse.setProvider("paystack");
        expectedResponse.setProviderReference("ref_123");
        expectedResponse.setCheckoutUrl("https://checkout.example.com");

        when(transactionHelper.preparePayment(any(Merchant.class), any(CreatePaymentRequest.class), eq("idem-1"), anyString()))
                .thenReturn(PreparePaymentResult.proceed(idempotencyRecord));
        when(providerConfigRepository.findByMerchantIdAndProvider_NameIgnoreCaseAndIsEnabledTrue(99L, "paystack"))
                .thenReturn(Optional.of(providerConfig));
        when(credentialStorageService.getProviderConfig("paystack", 99L)).thenReturn(Map.of("secretKey", "sk_test_123"));
        when(paymentProviderRegistry.getProvider("paystack")).thenReturn(paymentProvider);
        when(paymentProvider.CreatePaymentRequest(any(CreatePaymentRequest.class), anyMap())).thenReturn(providerResponse);
        when(transactionHelper.finalizePayment(eq(merchant), eq(providerConfig), eq(providerResponse), eq(idempotencyRecord), eq(request), eq("paystack")))
                .thenReturn(expectedResponse);

        PaymentResponse response = paymentService.createPayment(request, merchant, "idem-1");

        assertEquals("paystack", response.getProvider());
        assertEquals("ref_123", response.getProviderReference());
        assertEquals("https://checkout.example.com", response.getCheckoutUrl());

        verify(providerConfigRepository, never()).findByMerchantIdAndIsEnabledTrue(anyLong());
        verify(transactionHelper, never()).failIdempotencyRecord(any(IdempotencyKey.class));
    }

    @Test
    void createPayment_WithoutProviderAndMultipleEnabledConfigs_ThrowsClearError_AndMarksIdempotencyFailed() {
        Merchant merchant = new Merchant();
        merchant.setId(5L);

        IdempotencyKey idempotencyRecord = new IdempotencyKey();

        Provider p1 = new Provider();
        p1.setName("stripe");
        Provider p2 = new Provider();
        p2.setName("paystack");

        ProviderConfig c1 = new ProviderConfig();
        c1.setProvider(p1);
        ProviderConfig c2 = new ProviderConfig();
        c2.setProvider(p2);

        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setAmount(new BigDecimal("50.00"));
        request.setCurrency("USD");
        request.setDescription("Order #2");
        request.setEmail("customer@example.com");

        when(transactionHelper.preparePayment(any(Merchant.class), any(CreatePaymentRequest.class), eq("idem-2"), anyString()))
                .thenReturn(PreparePaymentResult.proceed(idempotencyRecord));
        when(providerConfigRepository.findByMerchantIdAndIsEnabledTrue(5L)).thenReturn(List.of(c1, c2));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> paymentService.createPayment(request, merchant, "idem-2")
        );

        assertTrue(ex.getMessage().contains("Multiple providers are enabled"));
        verify(paymentProviderRegistry, never()).getProvider(anyString());
        verify(transactionHelper).failIdempotencyRecord(idempotencyRecord);
    }
}
