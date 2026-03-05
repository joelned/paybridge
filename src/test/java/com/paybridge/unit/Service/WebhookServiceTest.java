package com.paybridge.unit.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paybridge.Models.Entities.Merchant;
import com.paybridge.Models.Entities.Payment;
import com.paybridge.Models.Entities.ProcessedWebhookEvent;
import com.paybridge.Models.Enums.PaymentStatus;
import com.paybridge.Repositories.PaymentRepository;
import com.paybridge.Repositories.ProcessedWebhookEventRepository;
import com.paybridge.Services.CredentialStorageService;
import com.paybridge.Services.WebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private ProcessedWebhookEventRepository processedWebhookEventRepository;
    @Mock
    private CredentialStorageService credentialStorageService;

    private WebhookService webhookService;

    @BeforeEach
    void setUp() {
        webhookService = new WebhookService(
                paymentRepository,
                processedWebhookEventRepository,
                credentialStorageService,
                new ObjectMapper()
        );
    }

    @Test
    void handlePaystackWebhook_ValidSignature_UpdatesToSucceededAndMarksProcessed() {
        Payment payment = buildPayment(PaymentStatus.PENDING, "ref_100");
        String payload = "{\"event\":\"charge.success\",\"data\":{\"id\":\"9001\",\"reference\":\"ref_100\",\"status\":\"success\"}}";
        String signature = sign(payload, "sk_test_secret");

        when(paymentRepository.findByProviderReferenceAndProvider_NameIgnoreCase("ref_100", "paystack"))
                .thenReturn(Optional.of(payment));
        when(credentialStorageService.getProviderConfig("paystack", 11L)).thenReturn(Map.of("secretKey", "sk_test_secret"));
        when(processedWebhookEventRepository.existsByProviderAndEventId("paystack", "9001")).thenReturn(false);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(processedWebhookEventRepository.save(any(ProcessedWebhookEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = webhookService.handlePaystackWebhook(payload, signature);

        assertEquals(true, result.get("processed"));
        assertEquals("SUCCEEDED", result.get("status"));
        assertEquals(PaymentStatus.SUCCEEDED, payment.getStatus());
        verify(processedWebhookEventRepository).save(any(ProcessedWebhookEvent.class));
    }

    @Test
    void handlePaystackWebhook_DuplicateEvent_DoesNotMutatePayment() {
        Payment payment = buildPayment(PaymentStatus.PENDING, "dup_ref");
        String payload = "{\"event\":\"charge.success\",\"data\":{\"id\":\"evt_dup\",\"reference\":\"dup_ref\",\"status\":\"success\"}}";
        String signature = sign(payload, "sk_test_secret");

        when(paymentRepository.findByProviderReferenceAndProvider_NameIgnoreCase("dup_ref", "paystack"))
                .thenReturn(Optional.of(payment));
        when(credentialStorageService.getProviderConfig("paystack", 11L)).thenReturn(Map.of("secretKey", "sk_test_secret"));
        when(processedWebhookEventRepository.existsByProviderAndEventId("paystack", "evt_dup")).thenReturn(true);

        Map<String, Object> result = webhookService.handlePaystackWebhook(payload, signature);

        assertEquals(true, result.get("duplicate"));
        verify(paymentRepository, never()).save(any(Payment.class));
        verify(processedWebhookEventRepository, never()).save(any(ProcessedWebhookEvent.class));
    }

    @Test
    void handlePaystackWebhook_SucceededPaymentNotDowngradedByFailedEvent() {
        Payment payment = buildPayment(PaymentStatus.SUCCEEDED, "ref_200");
        String payload = "{\"event\":\"charge.failed\",\"data\":{\"id\":\"evt_200\",\"reference\":\"ref_200\",\"status\":\"failed\"}}";
        String signature = sign(payload, "sk_test_secret");

        when(paymentRepository.findByProviderReferenceAndProvider_NameIgnoreCase("ref_200", "paystack"))
                .thenReturn(Optional.of(payment));
        when(credentialStorageService.getProviderConfig("paystack", 11L)).thenReturn(Map.of("secretKey", "sk_test_secret"));
        when(processedWebhookEventRepository.existsByProviderAndEventId("paystack", "evt_200")).thenReturn(false);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        webhookService.handlePaystackWebhook(payload, signature);

        assertEquals(PaymentStatus.SUCCEEDED, payment.getStatus());
    }

    @Test
    void handlePaystackWebhook_InvalidSignature_Throws() {
        Payment payment = buildPayment(PaymentStatus.PENDING, "ref_sig");
        String payload = "{\"event\":\"charge.success\",\"data\":{\"id\":\"evt_sig\",\"reference\":\"ref_sig\",\"status\":\"success\"}}";

        when(paymentRepository.findByProviderReferenceAndProvider_NameIgnoreCase("ref_sig", "paystack"))
                .thenReturn(Optional.of(payment));
        when(credentialStorageService.getProviderConfig("paystack", 11L)).thenReturn(Map.of("secretKey", "sk_test_secret"));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> webhookService.handlePaystackWebhook(payload, "bad-signature")
        );

        assertTrue(ex.getMessage().contains("Invalid Paystack webhook signature"));
    }

    @Test
    void handleStripeWebhook_WithoutConfiguredSigningSecret_Throws() {
        Payment payment = buildPayment(PaymentStatus.PENDING, "cs_test_missing_secret");
        String payload = "{\"id\":\"evt_1\",\"object\":\"event\",\"type\":\"checkout.session.completed\",\"data\":{\"object\":{\"id\":\"cs_test_missing_secret\",\"object\":\"checkout.session\"}}}";

        when(paymentRepository.findByProviderReferenceAndProvider_NameIgnoreCase("cs_test_missing_secret", "stripe"))
                .thenReturn(Optional.of(payment));
        when(credentialStorageService.getProviderConfig("stripe", 11L)).thenReturn(Map.of());
        ReflectionTestUtils.setField(webhookService, "stripeSigningSecret", "");

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> webhookService.handleStripeWebhook(payload, "t=1,v1=abc")
        );

        assertTrue(ex.getMessage().contains("signing secret is not configured"));
    }

    @Test
    void handleStripeWebhook_MissingSignatureHeader_Throws() {
        ReflectionTestUtils.setField(webhookService, "stripeSigningSecret", "whsec_test_123");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> webhookService.handleStripeWebhook("{\"id\":\"evt_1\"}", null)
        );

        assertTrue(ex.getMessage().contains("Missing Stripe-Signature"));
    }

    @Test
    void handleStripeWebhook_InvalidSignature_Throws() {
        Payment payment = buildPayment(PaymentStatus.PENDING, "cs_test_invalid_signature");
        String payload = "{\"id\":\"evt_1\",\"object\":\"event\",\"type\":\"checkout.session.completed\",\"data\":{\"object\":{\"id\":\"cs_test_invalid_signature\",\"object\":\"checkout.session\"}}}";

        when(paymentRepository.findByProviderReferenceAndProvider_NameIgnoreCase("cs_test_invalid_signature", "stripe"))
                .thenReturn(Optional.of(payment));
        when(credentialStorageService.getProviderConfig("stripe", 11L)).thenReturn(Map.of("webhookSecret", "whsec_test_123"));
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> webhookService.handleStripeWebhook(payload, "t=1,v1=invalid")
        );

        assertTrue(ex.getMessage().contains("Invalid Stripe webhook signature"));
    }

    private Payment buildPayment(PaymentStatus status, String providerReference) {
        Merchant merchant = new Merchant();
        merchant.setId(11L);

        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setMerchant(merchant);
        payment.setStatus(status);
        payment.setProviderReference(providerReference);
        return payment;
    }

    private String sign(String payload, String secret) {
        try {
            Mac sha512Hmac = Mac.getInstance("HmacSHA512");
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            sha512Hmac.init(keySpec);
            byte[] digest = sha512Hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
