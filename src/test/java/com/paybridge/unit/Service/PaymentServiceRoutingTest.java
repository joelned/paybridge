package com.paybridge.unit.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paybridge.Configs.PaymentProvider;
import com.paybridge.Models.DTOs.CreatePaymentRequest;
import com.paybridge.Models.DTOs.PaymentProviderResponse;
import com.paybridge.Models.DTOs.PaymentResponse;
import com.paybridge.Models.Entities.*;
import com.paybridge.Repositories.CustomerRepository;
import com.paybridge.Repositories.IdempotencyKeyRepository;
import com.paybridge.Repositories.PaymentRepository;
import com.paybridge.Repositories.ProviderConfigRepository;
import com.paybridge.Services.CredentialStorageService;
import com.paybridge.Services.PaymentProviderRegistry;
import com.paybridge.Services.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceRoutingTest {

    @Mock
    private IdempotencyKeyRepository idempotencyKeyRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private ProviderConfigRepository providerConfigRepository;
    @Mock
    private PaymentProviderRegistry paymentProviderRegistry;
    @Mock
    private CredentialStorageService credentialStorageService;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private PaymentProvider paymentProvider;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                idempotencyKeyRepository,
                paymentRepository,
                providerConfigRepository,
                paymentProviderRegistry,
                credentialStorageService,
                customerRepository,
                new ObjectMapper().findAndRegisterModules()
        );
    }

    @Test
    void createPayment_UsesExplicitRequestedProviderAndPersistsProviderReference() {
        Merchant merchant = new Merchant();
        merchant.setId(99L);

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

        when(idempotencyKeyRepository.findByIdempotencyKey("idem-1")).thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(idempotencyKeyRepository.save(any(IdempotencyKey.class))).thenAnswer(inv -> inv.getArgument(0));
        when(providerConfigRepository.findByMerchantIdAndProvider_NameIgnoreCaseAndIsEnabledTrue(99L, "paystack"))
                .thenReturn(Optional.of(providerConfig));
        when(credentialStorageService.getProviderConfig("paystack", 99L)).thenReturn(Map.of("secretKey", "sk_test_123"));
        when(paymentProviderRegistry.getProvider("paystack")).thenReturn(paymentProvider);
        when(paymentProvider.CreatePaymentRequest(any(CreatePaymentRequest.class), anyMap())).thenReturn(providerResponse);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        PaymentResponse response = paymentService.createPayment(request, merchant, "idem-1");

        assertEquals("paystack", response.getProvider());
        assertEquals("ref_123", response.getProviderReference());
        assertEquals("https://checkout.example.com", response.getCheckoutUrl());

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        assertEquals("ref_123", paymentCaptor.getValue().getProviderReference());

        verify(providerConfigRepository, never()).findByMerchantIdAndIsEnabledTrue(anyLong());
    }

    @Test
    void createPayment_WithoutProviderAndMultipleEnabledConfigs_ThrowsClearError() {
        Merchant merchant = new Merchant();
        merchant.setId(5L);

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

        when(idempotencyKeyRepository.findByIdempotencyKey("idem-2")).thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(idempotencyKeyRepository.save(any(IdempotencyKey.class))).thenAnswer(inv -> inv.getArgument(0));
        when(providerConfigRepository.findByMerchantIdAndIsEnabledTrue(5L)).thenReturn(List.of(c1, c2));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> paymentService.createPayment(request, merchant, "idem-2")
        );

        assertTrue(ex.getMessage().contains("Multiple providers are enabled"));
        verify(paymentProviderRegistry, never()).getProvider(anyString());
    }
}
